package scastie.metals

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.meta.internal.metals._
import scala.meta.internal.metals.Embedded
import scala.meta.internal.mtags._
import scala.meta.internal.pc.ScalaPresentationCompiler
import scala.meta.io.AbsolutePath
import scala.meta.pc.PresentationCompiler

import cats.effect.implicits.monadCancelOps_
import cats.effect.std.Semaphore
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all._
import meta.internal.mtags.MtagsEnrichments.XtensionAbsolutePath

trait BlockingServiceLoader[F[_]] {
  def load[T](cls: Class[T], className: String, classloader: URLClassLoader): F[T]
}

object BlockingServiceLoader {

  def instance[F[_]: Sync](semaphore: Semaphore[F]) = new BlockingServiceLoader[F] {

    /*
     * ServiceLoader is not thread safe. That's why we are blocking it's execution.
     */
    def load[T](cls: Class[T], className: String, classloader: URLClassLoader): F[T] =
      def load0: F[T] = Sync[F].delay {
        val services = ServiceLoader.load(cls, classloader).iterator()
        if (services.hasNext) services.next()
        else {
          // NOTE(olafur): ServiceLoader doesn't find the service on Appveyor for
          // some reason, I'm unable to reproduce on my computer. Here below we
          // fallback to manual classloading.
          val cls  = classloader.loadClass(className)
          val ctor = cls.getDeclaredConstructor()
          ctor.setAccessible(true)
          ctor.newInstance().asInstanceOf[T]
        }
      }

      Sync[F].uncancelable { poll =>
        poll(semaphore.acquire) *> poll(load0).guarantee(semaphore.release)
      }

  }

}

/*
 * PresentationCompilers is responsible for creation and configuration of PresentationCompiler.
 *
 * To properly provide all capabilities with documentation or enable resolution of symbol definition iterator
 * also handles creation of all necessary components:
 *  - SymbolIndex - shared by all presentation compilers enablind its search with `symbols`
 *  - Docstrings - responsible for providing markdown javadoc for `symbol`
 *
 * IMPORTANT, Presentation compilers are stored to make sure we are not loading same classes with Classloader.
 * They are also not thread safe that's why their creation for the first time for specific `scalaVersion` is blocking.
 */
class PresentationCompilers[F[_]: Async] {
  private val presentationCompilers: TrieMap[String, URLClassLoader] = TrieMap.empty

  // service loader must be blocking as it's not thread safe
  private val serviceLoader: F[BlockingServiceLoader[F]] = Semaphore[F](1).map(BlockingServiceLoader.instance[F])
  private val mtagsResolver                              = MtagsResolver.default()

  val index = OnDemandSymbolIndex.empty()
  val docs  = new Docstrings(index)

  def createPresentationCompiler(classpath: Seq[Path], version: String, mtags: MtagsBinaries): F[PresentationCompiler] =
    prepareClasspathSearch(classpath) >>= { classpathSearch =>
      (mtags match {
        case MtagsBinaries.BuildIn              => Sync[F].delay(ScalaPresentationCompiler(classpath = classpath))
        case artifacts: MtagsBinaries.Artifacts => presentationCompiler(artifacts, classpath)
      }).map(pc =>
        pc.newInstance("", classpath.filterNot(isSourceJar).asJava, Nil.asJava)
          .withSearch(ScastieSymbolSearch(docs, classpathSearch))
      )
    }

  private def prepareClasspathSearch(classpath: Seq[Path]): F[ClasspathSearch] = Sync[F].delay {
    import scala.meta.dialects._
    classpath.filter(isSourceJar).foreach { path =>
      index.addSourceJar(AbsolutePath(path), ScalaVersions.dialectForDependencyJar(AbsolutePath(path).filename))
    }

    ClasspathSearch.fromClasspath(classpath.filterNot(isSourceJar), ExcludedPackagesHandler.default)
  }

  private def isSourceJar(jarFile: Path): Boolean = {
    jarFile.getFileName.toString.endsWith("-sources.jar")
  }

  private def presentationCompiler(mtags: MtagsBinaries.Artifacts, scalaLibrary: Seq[Path]): F[PresentationCompiler] =
    Sync[F].delay {
      presentationCompilers.getOrElseUpdate(
        ScalaVersions.dropVendorSuffix(mtags.scalaVersion),
        newPresentationCompilerClassLoader(mtags, scalaLibrary)
      )
    } >>= (classloader =>
      serviceLoader.flatMap(
        _.load(classOf[PresentationCompiler], classOf[ScalaPresentationCompiler].getName(), classloader)
      )
    )

  private def newPresentationCompilerClassLoader(
    mtags: MtagsBinaries.Artifacts,
    classpath: Seq[Path]
  ): URLClassLoader = {
    val allJars = Iterator(mtags.jars, classpath).flatten
    val allURLs = allJars.map(_.toUri.toURL).toArray
    // Share classloader for a subset of types.
    val parent = new PresentationCompilerClassLoader(this.getClass.getClassLoader)
    new URLClassLoader(allURLs, parent)
  }

}
