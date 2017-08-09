addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.2.2")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC7")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.19")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.1")
addSbtPlugin("org.irundaia.sbt" % "sbt-sassify" % "1.4.8")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.5.0-M2")

libraryDependencies += "com.typesafe" % "config" % "1.3.1"
