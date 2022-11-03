package scastie.metals

import scala.collection.concurrent.TrieMap
import scala.meta.internal.metals._
import java.nio.file.Path
import scala.meta.internal.metals.Embedded
import scala.meta.internal.mtags._
import scala.meta.pc.PresentationCompiler
import scala.meta.internal.pc.ScalaPresentationCompiler
import scala.jdk.CollectionConverters._
import java.util.ServiceLoader
import java.net.URLClassLoader
import scala.meta.io.AbsolutePath
import meta.internal.mtags.MtagsEnrichments.XtensionAbsolutePath

object PresentationCompilers {
  private val presentationCompilers: TrieMap[String, URLClassLoader] = TrieMap.empty

  private val mtagsResolver = MtagsResolver.default()

  private def fetchPcDependencies(scalaVersion: String): (MtagsBinaries, Seq[Path]) =
    (mtagsResolver.resolve(scalaVersion).get, Embedded.scalaLibrary(scalaVersion))

  val index = OnDemandSymbolIndex.empty()
  val docs = new Docstrings(index)

  def createPresentationCompiler(classpath: Seq[Path], version: String, mtags: MtagsBinaries): PresentationCompiler = {
    import scala.meta.dialects._
    classpath.filter(isSourceJar).foreach { path =>
        index.addSourceJar(AbsolutePath(path), ScalaVersions.dialectForDependencyJar(AbsolutePath(path).filename))
    }

    val classpathSearch = ClasspathSearch.fromClasspath(classpath.filterNot(isSourceJar), ExcludedPackagesHandler.default)

    (mtags match {
      case MtagsBinaries.BuildIn => ScalaPresentationCompiler(classpath = classpath)
      case artifacts: MtagsBinaries.Artifacts => presentationCompiler(artifacts, classpath)
    }).newInstance("", classpath.filterNot(isSourceJar).asJava, Nil.asJava)
      .withSearch(ScastieSymbolSearch(docs, classpathSearch))
  }

  def isSourceJar(jarFile: Path): Boolean = {
    jarFile.getFileName.toString.endsWith("-sources.jar")
  }


  private def presentationCompiler(mtags: MtagsBinaries.Artifacts, scalaLibrary: Seq[Path]) = {
    val classloader = presentationCompilers.getOrElseUpdate(
      ScalaVersions.dropVendorSuffix(mtags.scalaVersion),
      newPresentationCompilerClassLoader(mtags, scalaLibrary)
    )

    serviceLoader(classOf[PresentationCompiler], classOf[ScalaPresentationCompiler].getName(), classloader)
  }

  private def newPresentationCompilerClassLoader(
      mtags: MtagsBinaries.Artifacts,
      classpath: Seq[Path]
  ): URLClassLoader = {
    val allJars = Iterator(mtags.jars, classpath).flatten
    val allURLs = allJars.map(_.toUri.toURL).toArray
    // Share classloader for a subset of types.
    val parent =
      new PresentationCompilerClassLoader(this.getClass.getClassLoader)
    new URLClassLoader(allURLs, parent)
  }

  private def serviceLoader[T](
      cls: Class[T],
      className: String,
      classloader: URLClassLoader
  ): T = {
    val services = ServiceLoader.load(cls, classloader).iterator()
    if (services.hasNext) services.next()
    else {
      // NOTE(olafur): ServiceLoader doesn't find the service on Appveyor for
      // some reason, I'm unable to reproduce on my computer. Here below we
      // fallback to manual classloading.
      val cls = classloader.loadClass(className)
      val ctor = cls.getDeclaredConstructor()
      ctor.setAccessible(true)
      ctor.newInstance().asInstanceOf[T]
    }
  }
}
