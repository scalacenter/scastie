resolvers += Resolver.bintrayRepo("projectseptemberinc", "maven")
libraryDependencies ++= Seq(
  "com.projectseptember" %% "freek" % "0.6.5",
  "org.typelevel" %% "cats" % "0.8.0"
)

scalacOptions ++= Vector("-Ypartial-unification")

// Kind-Projector //

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.0")

// if your project uses multiple Scala versions, use this for cross building
addCompilerPlugin(
  "org.spire-math" % "kind-projector" % "0.9.0" cross CrossVersion.binary
)

// if your project uses both 2.10 and polymorphic lambdas
libraryDependencies ++= (scalaBinaryVersion.value match {
  case "2.10" =>
    compilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
    ) :: Nil
  case _ =>
    Nil
})
