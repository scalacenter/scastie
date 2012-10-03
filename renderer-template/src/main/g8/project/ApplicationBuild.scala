import sbt._
import EvaluateConfigurations.{evaluateConfiguration => evaluate}
import sbt.Keys._
import com.olegych.scastie.{ScriptSecurityManager, SecuredRun}

object ApplicationBuild extends Build {
  val rendererWorker = Project(id = "rendererWorker", base = file("."),
    settings = Defaults.defaultSettings ++ Seq(
      runner in(Compile, run) <<= (taskTemporaryDirectory, scalaInstance) map { (nativeTmp, instance) =>
        new SecuredRun(instance, false, nativeTmp)
      },
      onLoad in Global := addDepsToState
    ))

  def addDepsToState(state: State): State = {
    val sessionSettings = state.get(Keys.sessionSettings).get
    val dependencies = extractDependencies(sessionSettings.currentEval(), getClass.getClassLoader, state)
    SessionSettings
        .reapply(sessionSettings.appendRaw(dependencies).appendRaw(onLoad in Global := idFun), state)
  }

  def extractDependencies(eval: compiler.Eval, loader: ClassLoader, state: State): Seq[Setting[_]] = {
    val scriptArg = "src/main/scala/test.scala"
    val script = file(scriptArg).getAbsoluteFile
    ScriptSecurityManager.hardenPermissions(try {
      val embeddedSettings = Script.blocks(script).flatMap { block =>
        val imports = List("import sbt._", "import Keys._")
        evaluate(eval, script.getPath, block.lines, imports, block.offset + 1)(loader)
      }
      embeddedSettings.flatMap {
        case setting if setting.key == libraryDependencies.scopedKey =>
          Project.transform(_ => GlobalScope, setting)
        case _ => Nil
      }
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        state.log.error(e.getMessage)
        state.log.trace(e)
        Nil
    })
  }
}