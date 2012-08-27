import scalariform.formatter.preferences._

resolvers += "xuwei-k repo" at "http://xuwei-k.github.com/mvn"

addCompilerPlugin("org.scala-tools.sxr" % "sxr_2.9.1" % "0.2.8-SNAPSHOT" )

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