resolvers += Resolver.url("olegych-repo", url("https://bitbucket.org/olegych/mvn/raw/default/ivy2/"))(Resolver.ivyStylePatterns)

addSbtPlugin("net.databinder.giter8" % "giter8-plugin" % "0.5.0-RC1")