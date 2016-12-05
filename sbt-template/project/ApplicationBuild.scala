import com.olegych.scastie.{ScriptSecurityManager, SecuredRun}
import sbt.EvaluateConfigurations._

import sbt._
import Keys._
import KeyRanks.DTask

import xsbti.{Reporter, Problem, Position, Severity, Maybe}
import upickle.default.{write ⇒ uwrite}

object ApplicationBuild extends Build {
  private val nl = System.lineSeparator

  private val runAll = TaskKey[Unit]("run-all")
  private val jdkVersion = settingKey[String]("")

  // compilerReporter is marked private in sbt
  private lazy val compilerReporter = TaskKey[Option[xsbti.Reporter]]("compilerReporter", "Experimental hook to listen (or send) compilation failure messages.", DTask)

  val rendererWorker = Project(id = "rendererWorker", base = file(".")).settings(
      updateOptions := updateOptions.value.withCachedResolution(true).withLatestSnapshots(false)
    , jdkVersion := "1.7"
    , scalacOptions += s"-target:jvm-${jdkVersion.value}"
    , javacOptions ++= Seq("-source", jdkVersion.value, "-target", jdkVersion.value)
    , compilerReporter in (Compile, compile) := Some(new xsbti.Reporter {
      private val buffer = collection.mutable.ArrayBuffer.empty[Problem]
      def reset(): Unit = buffer.clear()
      def hasErrors: Boolean = buffer.exists(_.severity == Severity.Error)
      def hasWarnings: Boolean = buffer.exists(_.severity == Severity.Warn)
      def printSummary(): Unit = {
        def toApi(p: Problem): sbtapi.Problem = {
          def toOption[T](m: Maybe[T]): Option[T] = {
            if(m.isEmpty) None
            else Some(m.get)
          }
          val severity =
            p.severity match {
              case xsbti.Severity.Info  => sbtapi.Info
              case xsbti.Severity.Warn  => sbtapi.Warning
              case xsbti.Severity.Error => sbtapi.Error
            }
          sbtapi.Problem(severity, toOption(p.position.offset).map(_.toInt), p.message)
        }
        println(uwrite(problems.map(toApi)))
      }
      def problems: Array[Problem] = buffer.toArray
      def log(pos: Position, msg: String, sev: Severity): Unit = {
        object MyProblem extends Problem {
          def category: String = "foo"
          def severity: Severity = sev
          def message: String = msg
          def position: Position = pos
          override def toString = s"$position:$severity: $message"
        }
        buffer.append(MyProblem)
      }
      def comment(pos: xsbti.Position, msg: String): Unit = ()
    })
    , runAll := {
      runAllTask(
        scalaVersion.value,
        (discoveredMainClasses in Compile).value,
        (fullClasspath in Compile).value,
        (runner in(Compile, run)).value,
        streams.value
      )
    }
    , runner in(Compile, run) := {
      new SecuredRun(
        scalaInstance.value,
        false,
        taskTemporaryDirectory.value
      )
    }
    , onLoad in Global := addDepsToState
  ).disablePlugins(coursier.CoursierPlugin)

  private def runAllTask(scalaV: String, discoveredMainClasses: Seq[String], fullClasspath: Keys.Classpath, runner: ScalaRun,
                 streams: Keys.TaskStreams) {

    println("*** runAllTask: " + scalaV + "***" )

    val mainClasses = if (discoveredMainClasses.isEmpty) Seq("Main") else discoveredMainClasses
    val errors = mainClasses.flatMap { mainClass =>
      runner.run(mainClass, Attributed.data(fullClasspath), Nil, streams.log)
    }
    if (errors.nonEmpty) {
      sys.error(errors.mkString("\n"))
    }
  }

  private def addDepsToState(state: State): State = {
    def extractDependencies(eval: compiler.Eval, loader: ClassLoader, state: State): Seq[Setting[_]] = {
      val forbiddenKeys = Set(fork, compile, onLoad, runAll)
      val scriptArg = "scastie/config.sbt"
      val script = file(scriptArg).getAbsoluteFile
      val lines = IO.readLines(script).toIndexedSeq

      try {
        ScriptSecurityManager.hardenPermissions {
          
          // val full = Seq(BlockStart + nl) ++ lines ++ Seq(nl + BlockEnd)
          
          // println("++++++")
          // println(full.mkString(nl))
          // println("++++++")

          // val blocks = extractBlocks(full)
          // val embeddedSettings = blocks.flatMap { block =>
          //   
          //   println("******")
          //   println(block.lines.mkString(nl))
          //   println("******")
            
          // }
          val imports = Seq("import sbt._", "import Keys._")
          evaluateConfiguration(eval, script, imports)(loader)

          // embeddedSettings.flatMap {
          //   case setting if !forbiddenKeys.exists(_.scopedKey == setting.key) =>
          //     setting
          //   case setting =>
          //     state.log.warn(s"ignored unsafe ${setting.toString}")
          //     Nil
          // }
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

    val extracted = Project.extract(state)
    val settings = extractDependencies(state.get(Keys.sessionSettings).get.currentEval(), extracted.currentLoader, state)
    val sessionSettings = sbt.Shim.SettingCompletions.setThis(state, extracted, settings, "").session
    BuiltinCommands.reapply(sessionSettings.appendRaw(onLoad in Global := idFun), extracted.structure, state)
  }


  /*
  Copyright (c) 2008-2014 Typesafe Inc, Mark Harrah, Grzegorz Kossakowski, Josh Suereth, Indrajit Raychaudhuri, Eugene Yokota, and other contributors.
  All rights reserved.
  */
  private final case class Block(offset: Int, lines: Seq[String])
  private val BlockStart = "/***"
  private val BlockEnd = "*/"
  private def extractBlocks(lines: Seq[String]): Seq[Block] = {
    def blocks(b: Block, acc: List[Block]): List[Block] =
      if (b.lines.isEmpty)
        acc.reverse
      else {
        val (dropped, blockToEnd) = b.lines.span { line => !line.startsWith(BlockStart) }
        val (block, remaining) = blockToEnd.span { line => !line.startsWith(BlockEnd) }
        val offset = b.offset + dropped.length
        blocks(Block(offset + block.length, remaining), Block(offset, block.drop(1)) :: acc)
      }
    blocks(Block(0, lines), Nil)
  }
  // End Copyright
}
