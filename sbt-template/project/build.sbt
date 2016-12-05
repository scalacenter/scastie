scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies += "org.scastie" %% "sbtapi" % "0.1.0-SNAPSHOT"  

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M12")

addSbtPlugin("com.felixmulder"  % "sbt-dotty"   % "0.1")
addSbtPlugin("org.scala-js"     % "sbt-scalajs" % "0.6.13")
// addSbtPlugin("org.scala-native" % "sbtplugin"   % "0.1-SNAPSHOT")
