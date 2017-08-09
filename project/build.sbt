import SbtShared.{apiProject, sbt210}

val api210 = apiProject(sbt210, fromSbt = true)
lazy val api210JVM = api210.jvm

libraryDependencies += "com.typesafe" % "config" % "1.3.1"

dependsOn(api210JVM)
