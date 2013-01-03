import com.typesafe.sbtscalariform.ScalariformPlugin._
import scala.Predef._
import scalariform.formatter.preferences._
import sbt._
import Keys._

object DefaultSettings {
  def apply: Seq[Setting[_]] = scalariformSettings ++ Seq(
    resolvers += Resolver.url("olegych-repo",
      url("https://bitbucket.org/olegych/mvn/raw/default/ivy2/"))(Resolver.ivyStylePatterns)
    , resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    , addSupportedCompilerPlugin("org.scala-tools.sxr" % "sxr" % "0.2.8-SNAPSHOT")(pluginVersions)
    , addSupportedCompilerPlugin("com.foursquare.lint" % "linter" % "0.1-SNAPSHOT")(pluginVersions)
    , scalacOptions <+= (scalaSource in Compile) map {"-P:sxr:base-directory:" + _.getAbsolutePath}
    , scalacOptions <+= baseDirectory map { base =>
      val linkFile = base / "sxr.links"
      "-P:sxr:link-file:" + linkFile.getAbsolutePath
    }
    , ScalariformKeys.preferences := FormattingPreferences().
        setPreference(AlignParameters, true).
        setPreference(AlignSingleLineCaseStatements, true).
        setPreference(CompactControlReadability, true).
        setPreference(PreserveDanglingCloseParenthesis, true).
        setPreference(DoubleIndentClassDeclaration, true)
    //to be able to detect prompt
    , shellPrompt := (_ => "$uniqueId$\n")
    , traceLevel := 1000
    , crossPaths := false
  )

  val pluginVersions: PartialFunction[(String, ModuleID), ModuleID] = {
    case ("2.9.2", module) => module.cross(CrossVersion.full)
    case ("2.10.0", module) => module.cross(CrossVersion.binary)
  }

  def addSupportedCompilerPlugin(module: ModuleID)
                                (version: PartialFunction[(String, ModuleID), ModuleID]): Project.Setting[Seq[ModuleID]] =
    libraryDependencies <++= (scalaVersion) { scalaVersion =>
      version.lift(scalaVersion, module).map(compilerPlugin(_)).toList
    }
}
