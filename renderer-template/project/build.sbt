scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies += "org.scastie" %% "sbtapi" % "0.1.0-SNAPSHOT"  

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.felixmulder"  % "sbt-dotty"   % "0.1")
addSbtPlugin("org.scala-js"     % "sbt-scalajs" % "0.6.13")
addSbtPlugin("org.scala-native" % "sbt-scala-native"   % "0.1.0-SNAPSHOT")
