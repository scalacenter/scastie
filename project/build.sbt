import SbtShared.{apiProject, latest212}

val api212 = apiProject(latest212, fromSbt = true)
lazy val api212JVM = api212.jvm

libraryDependencies += "com.typesafe" % "config" % "1.3.1"

dependsOn(api212JVM)
