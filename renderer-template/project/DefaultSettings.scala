import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._
import sbt._
import sbt.Keys._

object DefaultSettings {
  val sxrModule = "org.scala-sbt.sxr" %% "sxr" % "0.3.1-SCASTIE"
  def apply: Seq[Setting[_]] = scalariformSettings ++ Seq(
    resolvers += Resolver.url("olegych-repo",
      url("https://bitbucket.org/olegych/mvn/raw/default/ivy2/"))(Resolver.ivyStylePatterns)
    , resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    , addSupportedCompilerPlugin(sxrModule)(sxrVersion)
    , addSupportedCompilerPlugin("com.foursquare.lint" % "linter" % "0.1.2")(pluginVersions)
    , scalacOptions
    , ScalariformKeys.preferences := FormattingPreferences().
        setPreference(AlignParameters, true).
        setPreference(AlignSingleLineCaseStatements, true).
        setPreference(CompactControlReadability, true).
        setPreference(PreserveDanglingCloseParenthesis, true).
        setPreference(DoubleIndentClassDeclaration, true)
    , traceLevel := 1000
    , crossPaths := false
    , crossTarget := file("target")
  )


  def scalacOptions: Def.Setting[Task[Seq[String]]] = {
    Keys.scalacOptions <++= (scalaSource in Compile, baseDirectory, scalaVersion) map {
      (scalaSource, baseDirectory, scalaVersion) =>
        val sxrOptions = if (sxrVersion.isDefinedAt(scalaVersion, sxrModule)) {
          Seq(
            "-P:sxr:base-directory:" + baseDirectory.getAbsolutePath,
            "-P:sxr:link-file:" + (baseDirectory / "sxr.links").getAbsolutePath)
        } else {
          Nil
        }
        val featureOptions = if (Is210(scalaVersion)) Seq("-feature") else Nil
        Seq("-deprecation", "-unchecked", "-Ywarn-all", "-Xcheckinit") ++ sxrOptions ++ featureOptions
    }
  }


  class IsStartsWith(startsWith: String) {
    def apply(scalaVersion: String) = scalaVersion.startsWith(startsWith)
  }

  object Is29 extends IsStartsWith("2.9")

  object Is210 extends IsStartsWith("2.10")

  val sxrVersion: PartialFunction[(String, ModuleID), ModuleID] = {
    case (v, module) if Is29(v)  => "org.scala-tools.sxr" % "sxr_2.9.2" % "0.2.8-SNAPSHOT"
    case (v, module) if Is210(v) => module
  }
  val pluginVersions: PartialFunction[(String, ModuleID), ModuleID] = {
    case (v, module) if Is29(v)  => module.copy(name = module.name + "_2.9.2")
    case (v, module) if Is210(v) => module.cross(CrossVersion.binary)
  }

  def addSupportedCompilerPlugin(module: ModuleID)
                                (version: PartialFunction[(String, ModuleID), ModuleID]): Def.Setting[Seq[ModuleID]] =
    libraryDependencies <++= scalaVersion { scalaVersion =>
      version.lift(scalaVersion, module).map(compilerPlugin).toList
    }
}

