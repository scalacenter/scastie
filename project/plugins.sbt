addSbtPlugin("com.eed3si9n"        % "sbt-assembly"        % "2.1.5")
addSbtPlugin("com.github.sbt"      % "sbt-native-packager" % "1.11.7")
addSbtPlugin("io.spray"            % "sbt-revolver"        % "0.10.0")
addSbtPlugin("se.marcuslonnberg"   % "sbt-docker"          % "1.11.0")
addSbtPlugin("com.eed3si9n"        % "sbt-buildinfo"       % "0.11.0")
addSbtPlugin("com.eed3si9n"        % "sbt-projectmatrix"   % "0.11.0")
addSbtPlugin("org.scala-js"        % "sbt-scalajs"         % SbtShared.ScalaJSVersions.current)
addSbtPlugin("com.github.reibitto" % "sbt-welcome"         % "0.5.0")

addSbtPlugin("org.olegych" %% "sbt-cached-ci" % "1.0.4")
/*
  Exclude scala-parser-combinators pulled in by sbt-converter (2.4.0) to avoid
  eviction conflict with sbt-native-packager which requires 1.1.2
 */
addSbtPlugin(
  ("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta45")
    .exclude("org.scala-lang.modules", "scala-parser-combinators_2.12")
)
addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

//workaround https://github.com/sbt/sbt/issues/5374
allExcludeDependencies ++= List(
  ExclusionRule().withOrganization("org.webjars").withName("envjs"),
  ExclusionRule().withOrganization("com.google.javascript").withName("closure-compiler-externs")
)
