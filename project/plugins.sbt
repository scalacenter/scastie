// Comment to get more information during initialization
logLevel := Level.Info

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.2" exclude("com.github.mpeltonen", "sbt-idea"))

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")
