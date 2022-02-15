addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.8.3")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % SbtShared.ScalaJSVersions.current)

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")
addSbtPlugin("org.olegych" %% "sbt-cached-ci" % "1.0.4")

//workaround https://github.com/sbt/sbt/issues/5374
allExcludeDependencies ++= List(
  ExclusionRule().withOrganization("org.webjars").withName("envjs"),
  ExclusionRule().withOrganization("com.google.javascript").withName("closure-compiler-externs"),
)
