import scalariform.formatter.preferences._

resolvers += Resolver.url("olegych-repo", url("https://bitbucket.org/olegych/mvn/raw/default/ivy2/"))(Resolver.ivyStylePatterns)

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

addCompilerPlugin("org.scala-tools.sxr" %% "sxr" % "0.2.8-SNAPSHOT" )

addCompilerPlugin("com.foursquare.lint" %% "linter" % "0.1-SNAPSHOT")

scalacOptions <+= scalaSource in Compile map { "-P:sxr:base-directory:" + _.getAbsolutePath }

scalacOptions <+= baseDirectory map { base =>
  val linkFile = base / "sxr.links"
  "-P:sxr:link-file:" + linkFile.getAbsolutePath
}

scalariformSettings

ScalariformKeys.preferences := FormattingPreferences().
  setPreference(AlignParameters, true).
  setPreference(AlignSingleLineCaseStatements, true).
  setPreference(CompactControlReadability, true).
  setPreference(PreserveDanglingCloseParenthesis, true).
  setPreference(DoubleIndentClassDeclaration, true)

//to be able to detect prompt
shellPrompt := (_ => "$uniqueId$\n")

traceLevel := 1000