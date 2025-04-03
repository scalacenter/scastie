package com.olegych.scastie
package sbtscastie

import scala.util.{Failure, Success, Try}

import sbt.*
import sbt.internal.inc.AnalyzingCompiler
import sbt.Keys.*

object SbtScastiePlugin extends AutoPlugin {

  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[sbt.Def.Setting[_]] =
    (CompilerReporter.setting +: sbt.internal.util.com.olegych.scastie.sbtscastie.RuntimeErrorLogger.settings) ++
      Seq(
        // workaround https://github.com/sbt/sbt/issues/5482
        Global / nio.Keys.onChangedBuildSource := nio.Keys.IgnoreSourceChanges,
        turbo := true,
        useSuperShell := false,
        autoStartServer := false,
        compilers := {
          val r = compilers.value
          // compile bridge to init everything on reload
          r.scalac() match {
            case c: AnalyzingCompiler => c.provider.fetchCompiledBridge(c.scalaInstance, streams.value.log)
            case _                    => ()
          }
          r
        },
        Compile / run / runner ~= { runner =>
          new ForkRun(null) {
            override def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Try[Unit] = {
              val prev = ScastieTrapExit.installManager()
              try {
                val exitCode = ScastieTrapExit(runner.run(mainClass, classpath, options, log), log)
                if (exitCode == 0) {
                  log.debug("Exited with code 0")
                  Success(())
                } else Failure(new MessageOnlyException("Nonzero exit code: " + exitCode))
              } finally {
                ScastieTrapExit.uninstallManager(prev)
              }
            }
          }
        },
        resolvers := {
          Seq[Resolver](
            Resolver
              .url("my-ivy-proxy-releases", url("http://scala-webapps.epfl.ch:8081/artifactory/scastie-ivy/"))(
                Resolver.ivyStylePatterns
              )
              .withAllowInsecureProtocol(true),
            "my-maven-proxy-releases" at "http://scala-webapps.epfl.ch:8081/artifactory/scastie-maven/" withAllowInsecureProtocol (true)
          ) ++ resolvers.value
        }
      )

}
