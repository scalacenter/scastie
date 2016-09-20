package sbt

import com.olegych.scastie.{ScriptSecurityManager, SecuredRun}
import sbt.EvaluateConfigurations._
import sbt.Keys._
import sbt._
import xsbti.{Reporter, Problem, Position, Severity, Maybe}

object ApplicationBuild extends Build {
  val runAll = TaskKey[Unit]("run-all")
  val jdkVersion = settingKey[String]("")
  val compileInfo = TaskKey[Unit]("compile-info")


  val rendererWorker = Project(id = "rendererWorker", base = file(".")).settings(Seq(
      updateOptions := updateOptions.value.withCachedResolution(true).withLatestSnapshots(false)
    , jdkVersion := "1.7"
    , scalacOptions += s"-target:jvm-${jdkVersion.value}"
    , javacOptions ++= Seq("-source", jdkVersion.value, "-target", jdkVersion.value)
    , compilerReporter in (Compile, compile) := Some(new xsbti.Reporter {
      private val buffer = collection.mutable.ArrayBuffer.empty[Problem]
      def toOption[T](m: Maybe[T]): Option[T] = {
        if(m.isEmpty) None
        else Some(m.get)
      }
      def reset(): Unit = buffer.clear()
      def hasErrors: Boolean = buffer.exists(_.severity == Severity.Error)
      def hasWarnings: Boolean = buffer.exists(_.severity == Severity.Warn)
      def printSummary(): Unit = {
        println(problems.map(p =>
          s"${p.severity} ${toOption(p.position.offset)} ${p.message}"
        ).mkString(System.lineSeparator))
      }
      def problems: Array[Problem] = buffer.toArray
      def log(pos: Position, msg: String, sev: Severity): Unit = {
        object MyProblem extends Problem {
          def category: String = null
          def severity: Severity = sev
          def message: String = msg
          def position: Position = pos
          override def toString = s"$position:$severity: $message"
        }
        buffer.append(MyProblem)
      }
      def comment(pos: xsbti.Position, msg: String): Unit = ()
    })
    , runAll <<=
      (discoveredMainClasses in Compile, fullClasspath in Compile, runner in(Compile, run), streams) map runAllTask
    , runner in(Compile, run) <<= (taskTemporaryDirectory, scalaInstance) map { (nativeTmp, instance) =>
      new SecuredRun(instance, false, nativeTmp)
    }
    , onLoad in Global := addDepsToState
  ): _*).disablePlugins(coursier.CoursierPlugin)

  def runAllTask(discoveredMainClasses: Seq[String], fullClasspath: Keys.Classpath, runner: ScalaRun,
                 streams: Keys.TaskStreams) {
    val mainClasses = if (discoveredMainClasses.isEmpty) Seq("Main") else discoveredMainClasses
    val errors = mainClasses.flatMap { mainClass =>
      runner.run(mainClass, Attributed.data(fullClasspath), Nil, streams.log)
    }
    if (errors.nonEmpty) {
      sys.error(errors.mkString("\n"))
    }
  }

  def addDepsToState(state: State): State = {
    val extracted = Project.extract(state)
    val settings = extractDependencies(state.get(Keys.sessionSettings).get.currentEval(), extracted.currentLoader, state)
    val sessionSettings = sbt.Shim.SettingCompletions.setThis(state, extracted, settings, "").session
    BuiltinCommands.reapply(sessionSettings.appendRaw(onLoad in Global := idFun), extracted.structure, state)
  }

  val forbiddenKeys = Set(fork, compile, onLoad, runAll)

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
          case setting if !forbiddenKeys.exists(_.scopedKey == setting.key) =>
            setting
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
