package scastie.metals

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.meta.internal.metals.Embedded
import scala.meta.internal.metals.MtagsBinaries
import scala.meta.internal.metals.MtagsResolver
import scala.meta.internal.metals.PresentationCompilerClassLoader
import scala.meta.internal.metals.ScalaVersions
import scala.meta.internal.pc.ScalaPresentationCompiler
import scala.meta.pc.PresentationCompiler
import scala.meta.pc.SymbolSearch
import scala.meta.internal.metals.MetalsSymbolSearch
import scala.meta.internal.metals.Docstrings.apply
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.metals.StandaloneSymbolSearch
import scala.meta.internal.metals.WorkspaceSymbolProvider
import scala.meta.internal.pc.EmptySymbolSearch
import scala.meta.internal.pc.WorkspaceSymbolSearch
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.Docstrings
import scala.meta.pc.SymbolSearchVisitor
import java.{util => ju}
import java.net.URI
import org.eclipse.lsp4j.Location;
import scala.meta.pc.ParentSymbols
import java.util.Optional
import scala.meta.pc.SymbolDocumentation
import scala.meta.Dialect
import scala.meta.pc.PresentationCompilerConfig
import scala.meta.internal.mtags.GlobalSymbolIndex
import java.nio.file.Files
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.ExcludedPackagesHandler
import scala.meta.internal.metals.DefinitionProvider
import scala.meta.internal.metals.WorkspaceSymbolQuery
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import scala.meta.internal.metals.ClasspathSearch
import scala.jdk.CollectionConverters._
 import meta.internal.mtags.MtagsEnrichments.XtensionAbsolutePath

object PresentationCompilers {
  private val presentationCompilers: TrieMap[String, URLClassLoader] = TrieMap.empty

private val mtagsResolver = MtagsResolver.default()

  private def fetchPcDependencies(scalaVersion: String): (MtagsBinaries, Seq[Path]) =
    (mtagsResolver.resolve(scalaVersion).get, Embedded.scalaLibrary(scalaVersion))

  def createPresentationCompiler(classpath: Seq[Path], version: String, mtags: MtagsBinaries): PresentationCompiler = {
    import scala.meta.dialects._
    classpath.filter(isSourceJar).foreach( path => index.addSourceJar(AbsolutePath(path), ScalaVersions.dialectForDependencyJar(AbsolutePath(path).filename)))

    (mtags match {
      case MtagsBinaries.BuildIn => ScalaPresentationCompiler(classpath = classpath)
      case artifacts: MtagsBinaries.Artifacts => presentationCompiler(artifacts, classpath)
    }).newInstance("", classpath.filterNot(isSourceJar).asJava, Nil.asJava)
      .withSearch(ScastieSymbolSearch())
  }

  val index = OnDemandSymbolIndex.empty()
  val docs = new Docstrings(index)

  class ScastieSymbolSearch() extends SymbolSearch {
    override def search(
        query: String,
        buildTargetIdentifier: String,
        visitor: SymbolSearchVisitor
    ): SymbolSearch.Result = {
      SymbolSearch.Result.COMPLETE
    }

    override def searchMethods(
        query: String,
        buildTargetIdentifier: String,
        visitor: SymbolSearchVisitor
    ): SymbolSearch.Result = {
      SymbolSearch.Result.COMPLETE
    }

    def definition(symbol: String, source: URI): ju.List[Location] = {
      ju.Collections.emptyList()
    }

    def definitionSourceToplevels(
        symbol: String,
        sourceUri: URI
    ): ju.List[String] = {
      ju.Collections.emptyList()
    }

    override def documentation(
        symbol: String,
        parents: ParentSymbols
    ): Optional[SymbolDocumentation] =
      docs.documentation(symbol, parents)
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
