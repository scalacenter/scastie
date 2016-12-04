// import com.typesafe.sbt.SbtScalariform._
import sbt.Keys._
import sbt._

// import scalariform.formatter.preferences._

object DefaultSettings {
  def apply: Seq[Setting[_]] =
    // scalariformSettings ++
    Seq(
      // resolvers += Resolver.url("olegych-repo",
      //   url("https://bitbucket.org/olegych/mvn/raw/default/ivy2/"))(Resolver.ivyStylePatterns)
      // , resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
      // , addSupportedCompilerPlugin(sxrVersion)
      // , addSupportedCompilerPlugin(linterVersion)
      scalacOptions
      // , ScalariformKeys.preferences := FormattingPreferences().
      //   setPreference(AlignParameters, true).
      //   setPreference(AlignSingleLineCaseStatements, true).
      //   setPreference(CompactControlReadability, true).
      //   setPreference(PreserveDanglingCloseParenthesis, true).
      //   setPreference(DoubleIndentClassDeclaration, true)
      ,
      traceLevel := 1000,
      crossPaths := false,
      crossTarget := file("target")
    )

  def scalacOptions: Def.Setting[Task[Seq[String]]] = {
    Keys.scalacOptions <++= (scalaSource in Compile,
                             baseDirectory,
                             scalaVersion) map {
      (scalaSource, baseDirectory, scalaVersion) =>
        val featureOptions =
          if (Is210(scalaVersion) || Is211(scalaVersion)) List("-feature")
          else Nil
        val warnAll = if (Is210(scalaVersion)) List("-Ywarn-all") else Nil
        List("-deprecation", "-unchecked", "-Xcheckinit") ++
          featureOptions ++
          warnAll
      // sxrOptions(scalaVersion, baseDirectory) ++
    }
  }

  class IsStartsWith(startsWith: String) {
    def apply(scalaVersion: String) = scalaVersion.startsWith(startsWith)
    def unapply(scalaVersion: String): Option[String] =
      Some(scalaVersion).filter(apply)
  }

  object Is29  extends IsStartsWith("2.9")
  object Is210 extends IsStartsWith("2.10")
  object Is211 extends IsStartsWith("2.11")

  // val sxrVersion: PartialFunction[String, ModuleID] = {
  //   case Is29(v) => "org.scala-tools.sxr" % "sxr_2.9.2" % "0.2.8-SNAPSHOT"
  //   case Is210(v) => "org.scala-sbt.sxr" %% "sxr" % "0.3.1-SCASTIE"
  //   case Is211(v) => "org.scala-sbt.sxr" %% "sxr" % "0.3.2-SCASTIE"
  // }
  // def sxrOptions(scalaVersion: String, baseDirectory: File): Seq[String] = {
  //   val linksVersion = scalaVersion match {
  //     case Is29(v) => Some("2.10")
  //     case Is210(v) => Some("2.10")
  //     case Is211(v) => Some("2.11")
  //     case _ => None
  //   }
  //   linksVersion.map(v => Seq(
  //     "-P:sxr:base-directory:" + baseDirectory.getAbsolutePath
  //     , "-P:sxr:link-file:" + (baseDirectory / s"sxr$v.links").getAbsolutePath
  //   )).getOrElse(Nil)
  // }
  // val linterVersion: PartialFunction[String, ModuleID] = {
  //   case Is29(v) => "com.foursquare.lint" % "linter_2.9.2" % "0.1.2"
  //   case Is210(v) => "com.foursquare.lint" % "linter" % "0.1.2" cross CrossVersion.binary
  // }

  def addSupportedCompilerPlugin(
      version: PartialFunction[String, ModuleID]): Def.Setting[Seq[ModuleID]] =
    libraryDependencies <++= scalaVersion { scalaVersion =>
      version.lift(scalaVersion).map(compilerPlugin).toList
    }
}
