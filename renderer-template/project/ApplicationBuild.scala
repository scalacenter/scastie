import com.olegych.scastie.{ScriptSecurityManager, SecuredRun}
import sbt.EvaluateConfigurations._
import sbt.Keys._
import sbt._

object ApplicationBuild extends Build {
  val runAll = TaskKey[Unit]("run-all")
  val jdkVersion = settingKey[String]("")

  val rendererWorker = Project(id = "rendererWorker", base = file(".")).settings(DefaultSettings.apply ++ Seq(
    updateOptions := updateOptions.value.withCachedResolution(true).withLatestSnapshots(false)
    , jdkVersion := "1.7"
    , scalacOptions += s"-target:jvm-${jdkVersion.value}"
    , javacOptions ++= Seq("-source", jdkVersion.value, "-target", jdkVersion.value)
    , runAll <<=
      (discoveredMainClasses in Compile, fullClasspath in Compile, runner in(Compile, run), streams) map runAllTask
    , runner in(Compile, run) <<= (taskTemporaryDirectory, scalaInstance) map { (nativeTmp, instance) =>
      new SecuredRun(instance, false, nativeTmp)
    }
    , onLoad in Global := addDepsToState
  ): _*)

  def runAllTask(discoveredMainClasses: Seq[String], fullClasspath: Keys.Classpath, runner: ScalaRun,
                 streams: Keys.TaskStreams) {
    val errors = discoveredMainClasses.flatMap { mainClass =>
      runner.run(mainClass, Attributed.data(fullClasspath), Nil, streams.log)
    }
    if (errors.nonEmpty) {
      sys.error(errors.mkString("\n"))
    }
  }

  def addDepsToState(state: State): State = {
    val sessionSettings = state.get(Keys.sessionSettings).get
    val dependencies = extractDependencies(sessionSettings.currentEval(),
      Project.extract(state).currentLoader, state)
    SessionSettings
      .reapply(sessionSettings.appendRaw(dependencies).appendRaw(onLoad in Global := idFun), state)
  }

  val allowedKeys = Set[Init[_]#KeyedInitialize[_]](libraryDependencies, scalaVersion, resolvers, scalacOptions, sbtPlugin)

  def extractDependencies(eval: compiler.Eval, loader: ClassLoader, state: State): Seq[Setting[_]] = {
    val scriptArg = "src/main/scala/test.scala"
    val script = file(scriptArg).getAbsoluteFile
    try {
      ScriptSecurityManager.hardenPermissions {
        val embeddedSettings = Script.blocks(script).flatMap { block =>
          val imports = List("import sbt._", "import Keys._")
          evaluateConfiguration(eval, script, block.lines, imports, block.offset + 1)(loader)
        }
        embeddedSettings.flatMap {
          case setting if allowedKeys.exists(_.scopedKey == setting.key) =>
            Project.transform(_ => GlobalScope, setting)
          case setting =>
            state.log.warn(s"ignored unsafe ${setting.toString}")
            Nil
        }
      }
    } catch {
      case e: Throwable =>
        state.log.error(e.getClass.toString)
        state.log.error(e.getMessage)
        e.getStackTrace.take(100).foreach(e => state.log.error(e.toString))
        state.log.trace(e)
        Nil
    }
  }
}
