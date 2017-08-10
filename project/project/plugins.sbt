addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.19")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

unmanagedSourceDirectories in Compile += {
  baseDirectory.value.getParentFile.getParentFile / "sbt-shared"
}