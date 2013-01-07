import sbt._
import EvaluateConfigurations.{evaluateConfiguration => evaluate}
import sbt.Build._
import sbt.Keys._
import com.olegych.scastie.{ScriptSecurityManager, SecuredRun}

object ApplicationBuild extends Build {
  val runAll = TaskKey[Unit]("run-all")

  val rendererWorker = Project(id = "rendererWorker", base = file("."),
    settings = Defaults.defaultSettings ++ DefaultSettings.apply ++ Seq(
      runAll <<=
          (discoveredMainClasses in Compile, fullClasspath in Compile, runner in(Compile, run), streams) map
              runAllTask,
      runner in(Compile, run) <<= (taskTemporaryDirectory, scalaInstance) map { (nativeTmp, instance) =>
        new SecuredRun(instance, false, nativeTmp)
      },
      onLoad in Global := addDepsToState
    ))


  def runAllTask(discoveredMainClasses: Seq[String], fullClasspath: Keys.Classpath, runner: ScalaRun,
                 streams: Keys.TaskStreams) {
    val errors = discoveredMainClasses.flatMap { mainClass =>
      runner.run(mainClass, data(fullClasspath), Nil, streams.log)
    }
    if (!errors.isEmpty) {
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

  val allowedKeys = Set(libraryDependencies, scalaVersion)

  def extractDependencies(eval: compiler.Eval, loader: ClassLoader, state: State): Seq[Setting[_]] = {
    val scriptArg = "src/main/scala/test.scala"
    val script = file(scriptArg).getAbsoluteFile
    try {
      ScriptSecurityManager.hardenPermissions {
        val embeddedSettings = Script.blocks(script).flatMap { block =>
          val imports = List("import sbt._", "import Keys._")
          evaluate(eval, script.getPath, block.lines, imports, block.offset + 1)(loader)
        }
        embeddedSettings.flatMap {
          case setting if allowedKeys.exists(_.scopedKey == setting.key) =>
            Project.transform(_ => GlobalScope, setting)
          case _ => Nil
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