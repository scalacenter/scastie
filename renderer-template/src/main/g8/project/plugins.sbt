resolvers += Resolver.url("olegych-repo", url("https://bitbucket.org/olegych/mvn/raw/default/ivy2/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.5.1")

libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.4-SNAPSHOT"