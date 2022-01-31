lazy val apiSbt = SbtShared.sbtApiProject
dependsOn(apiSbt)
libraryDependencies += "com.typesafe" % "config" % "1.3.4"
