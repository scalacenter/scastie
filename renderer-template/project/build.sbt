lazy val sbtApi210 = project.settings(
  scalaVersion := "2.10.6"
, libraryDependencies += "com.lihaoyi" %% "upickle"  % "0.4.0"
, scalaSource in Compile := (baseDirectory in ThisBuild).value / "sbt-api"
)

lazy val template = project.in(file("."))
  .settings(
    scalacOptions ++= Seq("-deprecation", "-feature")
  , libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.6-SCASTIE"
  , resolvers += Resolver.url("olegych-repo",
      url("https://bitbucket.org/olegych/mvn/raw/default/ivy2/"))(Resolver.ivyStylePatterns)
  , addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.2.0")
  , addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M12")
  // , addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.2.6")
  , addSbtPlugin("com.felixmulder" % "sbt-dotty" % "0.1")
  ).dependsOn(sbtApi210)
