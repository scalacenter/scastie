package com.olegych.scastie
package sbtscastie

import sbt.Keys._
import sbt._
import sbt.internal.inc.AnalyzingCompiler

object SbtScastiePlugin extends AutoPlugin {

  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[sbt.Def.Setting[_]] =
    (CompilerReporter.setting +: RuntimeErrorLogger.settings) ++
      Seq(
        //workaround https://github.com/sbt/sbt/issues/5482
        Global / nio.Keys.onChangedBuildSource := nio.Keys.IgnoreSourceChanges,
        autoStartServer := false,
        compilers := {
          val r = compilers.value
          //compile bridge to init everything on reload
          r.scalac() match {
            case c: AnalyzingCompiler => c.force(streams.value.log)
            case _                    => ()
          }
          r
        },
        resolvers := {
          Seq[Resolver](
            Resolver
              .url("my-ivy-proxy-releases", url("http://scala-webapps.epfl.ch:8081/artifactory/scastie-ivy/"))(Resolver.ivyStylePatterns)
              .withAllowInsecureProtocol(true),
            "my-maven-proxy-releases" at "http://scala-webapps.epfl.ch:8081/artifactory/scastie-maven/" withAllowInsecureProtocol (true),
          ) ++ resolvers.value
        },
      )
}
