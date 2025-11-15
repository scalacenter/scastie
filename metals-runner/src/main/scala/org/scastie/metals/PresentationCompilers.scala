package org.scastie.metals

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.meta.dialects._
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
import scala.meta.pc.RawPresentationCompiler
import scala.meta.internal.metals.clients.language.RawMetalsQuickPickResult
import ch.epfl.scala.debugadapter.ScalaVersion
import cats.effect.IO

trait BlockingServiceLoader {
  def load[T](cls: Class[T], className: String, classloader: URLClassLoader): IO[T]
}

object BlockingServiceLoader {

  def instance(semaphore: Semaphore[IO]) = new BlockingServiceLoader {

    /*
     * ServiceLoader is not thread safe. That's why we are blocking it's execution.
     */
    def load[T](cls: Class[T], className: String, classloader: URLClassLoader): IO[T] =
      def load0: IO[T] = IO.blocking {
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

      IO.uncancelable { poll =>
        poll(semaphore.acquire) *> poll(load0).guarantee(semaphore.release)
      }

  }

}

/*
 * PresentationCompilers is responsible for creation and configuration of PresentationCompiler.
 *
 * To properly provide all capabilities with documentation or enable resolution of symbol definition iterator
 * also handles creation of all necessary components:
 *  - SymbolIndex - shared by all presentation compilers enabling its search with `symbols`
 *  - Docstrings - responsible for providing markdown javadoc for `symbol`
 *
 * IMPORTANT, Presentation compilers are stored to make sure we are not loading same classes with Classloader.
 * They are also not thread safe that's why their creation for the first time for specific `scalaVersion` is blocking.
 */
class PresentationCompilers(metalsWorkingDirectory: Path) {
  private val presentationCompilers: TrieMap[String, URLClassLoader] = TrieMap.empty

  // service loader must be blocking as it's not thread safe
  private val serviceLoader: IO[BlockingServiceLoader] = Semaphore[IO](1).map(BlockingServiceLoader.instance)

  val index = OnDemandSymbolIndex.empty()(
    using EmptyReportContext
  )

  given reportContext: ReportContext = EmptyReportContext

  val docs = new Docstrings(index)

  JdkSources().foreach(jdk => index.addSourceJar(jdk, Scala213))

  def createPresentationCompiler(classpath: Seq[Path], version: String, mtags: MtagsBinaries): IO[PresentationCompiler | RawPresentationCompiler] =
    prepareClasspathSearch(classpath, version) >>= { classpathSearch =>
      (mtags match {
        case MtagsBinaries.BuildIn              => IO(ScalaPresentationCompiler(classpath = classpath))
        case artifacts: MtagsBinaries.Artifacts => presentationCompiler(artifacts, classpath)
      }).map {
        case raw: RawPresentationCompiler =>
          raw.newInstance("", classpath.filterNot(isSourceJar).asJava, Nil.asJava)
            .withSearch(ScastieSymbolSearch(docs, classpathSearch))
            .withWorkspace(metalsWorkingDirectory)
        case normal: PresentationCompiler =>
          normal.newInstance("", classpath.filterNot(isSourceJar).asJava, Nil.asJava)
            .withSearch(ScastieSymbolSearch(docs, classpathSearch))
            .withWorkspace(metalsWorkingDirectory)
      }
    }

  private def prepareClasspathSearch(classpath: Seq[Path], version: String): IO[ClasspathSearch] = IO {
    classpath.filter(isSourceJar).foreach { path => {
      val libVersion = ScalaVersions.scalaBinaryVersionFromJarName(path.getFileName.toString).getOrElse(version)
      index.addSourceJar(AbsolutePath(path), ScalaVersions.dialectForScalaVersion(libVersion, true))
    }}

    ClasspathSearch.fromClasspath(classpath.filterNot(isSourceJar), ExcludedPackagesHandler.default)
  }

  private def isSourceJar(jarFile: Path): Boolean = {
    jarFile.getFileName.toString.endsWith("-sources.jar")
  }

  private def presentationCompiler(mtags: MtagsBinaries.Artifacts, scalaLibrary: Seq[Path]): IO[PresentationCompiler | RawPresentationCompiler] =
    IO {
      presentationCompilers.getOrElseUpdate(
        ScalaVersions.dropVendorSuffix(mtags.scalaVersion),
        newPresentationCompilerClassLoader(mtags, scalaLibrary)
      )
    } >>= (classloader =>
      serviceLoader.flatMap(serviceLoader =>
        if (ScalaVersion(mtags.scalaVersion) >= ScalaVersion("3.8.0"))
          serviceLoader.load(classOf[RawPresentationCompiler], "dotty.tools.pc.RawScalaPresentationCompiler" , classloader)
        else if (mtags.isScala3PresentationCompiler)
          serviceLoader.load(classOf[PresentationCompiler], "dotty.tools.pc.ScalaPresentationCompiler" , classloader)
        else
          serviceLoader.load(classOf[PresentationCompiler], classOf[ScalaPresentationCompiler].getName() , classloader)
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
