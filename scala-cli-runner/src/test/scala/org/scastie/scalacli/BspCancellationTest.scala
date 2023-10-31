package org.scastie.scalacli

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import org.scastie.util.ScastieFileUtil
import java.nio.file.Paths
import org.scastie.api.SnippetId
import org.scastie.api._
import org.scastie.runtime.api._
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestActor.AutoPilot
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scastie.runtime.api._
import org.scastie.api._
import org.scastie.util.SbtTask
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.duration._
import akka.testkit.TestActorRef
import org.scastie.util.ScalaCliActorTask
import java.nio.file.Files

class BspCancellationTest extends TestKit(ActorSystem("ScalaCliActorTest")) with ImplicitSender with AnyFunSuiteLike with BeforeAndAfterAll {
  val workingDir = Files.createTempDirectory("scastie")
  println(workingDir)

  setAutoPilot(new AutoPilot {
    def run(sender: ActorRef, msg: Any): AutoPilot = {
      sender ! s"reply to $msg"
      this
    }
  })

  private val macroCode =
    """import scala.quoted._
      |
      |object SleepMacro:
      |  inline def sleep(inline time: Int) =
      |    ${ wait('time) }
      |
      |  def wait(x: Expr[Int])(using Quotes): Expr[Any] =
      |    Thread.sleep(x.valueOrAbort)
      |    x
      |""".stripMargin
  Files.writeString(workingDir.resolve("SleepMacro.scala"), macroCode)

  def longCompilation(time: Int): String =
    s"""//> using scala 3
       |//> using file SleepMacro.scala
       |
       |@main def hello =
       |  SleepMacro.sleep($time)
       |  println("test")
       |""".stripMargin


  def longRuntime(time: Int): String =
    s"""//> using scala 3
       |
       |@main def hello =
       |  Thread.sleep($time)
       |  println("test")
       |""".stripMargin

  test("No bsp timeout") {
    runCode(longCompilation(1000), isWorksheet = false)(assertUserOutput("test"))
  }

  test("BSP Timeout") {
    runCode(longCompilation(15000), isWorksheet = false)(assertCompilationInfo { info =>
      assert(info.message == "Build Server Timeout Exception" )
    })
  }

  test("BSP Timeout Multiple snippets") {
    runCode(longCompilation(30000), isWorksheet = false)(assertCompilationInfo { info =>
      assert(info.message == "Build Server Timeout Exception" )
    })
    runCode(longCompilation(100), isWorksheet = false)(assertUserOutput("test"))

    runCode(longCompilation(30000), isWorksheet = false)(assertCompilationInfo { info =>
      assert(info.message == "Build Server Timeout Exception" )
    })
  }

  test("No Runtime Timeout") {
    runCode(longRuntime(1000), isWorksheet = false)(assertUserOutput("test"))
  }

  test("Runtime Timeout") {
    runCode(longRuntime(15000), isWorksheet = false)(assertCompilationInfo { info =>
      assert(info.message == "RuntimeError(Timeout exceeded.)" )
    })
  }

  // test("Runtime Timeout Multiple snippets") {
  //   def code(sleep: Int) =
  //     s"""//> using scala 3
  //        |
  //        |@main def hello =
  //        |  Thread.sleep(15000)
  //        |  println("test")
  //        |""".stripMargin
  // }

  // test("Runtime Timeout Multiple snippets") {
  //   def code(sleep: Int) =
  //     s"""//> using scala 3
  //        |
  //        |@main def hello =
  //        |  Thread.sleep(15000)
  //        |  println("test")
  //        |""".stripMargin
  // }

  // print("\u001b")

  // (1 to 2).foreach { i =>
  //   test(s"[$i] timeout") {
  //     runCode(s"Thread.sleep(${timeout.toMillis + 1000})", allowFailure = true)(progress => {
  //       // println(progress)
  //       progress.isTimeout
  //     })
  //   }

  //   test(s"[$i] after a timeout the sbt instance is ready to be used") {
  //     runCode("1 + 1")(progress => {
  //       val gotInstrumentation = progress.instrumentations.nonEmpty

  //       if (gotInstrumentation) {
  //         assert(
  //           progress.instrumentations == List(
  //             Instrumentation(Position(0, 5), Value("2", "Int"))
  //           )
  //         )
  //       }

  //       gotInstrumentation
  //     })
  //   }
  // }


  def assertCompilationInfo(infoAssert: Problem => Any)(progress: SnippetProgress): Boolean = {

    val gotCompilationError = progress.compilationInfos.nonEmpty

    if (gotCompilationError) {
      val info = progress.compilationInfos.head
      infoAssert(info)
    }

    gotCompilationError
  }

  def assertUserOutput(outputAssert: ProcessOutput => Any)(progress: SnippetProgress): Boolean = {
    progress.userOutput.map(outputAssert(_))
    progress.userOutput.isDefined
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val timeout = 40.seconds

  val scalaCliActor = system.actorOf(Props(new ScalaCliActor(runTimeout = timeout, isProduction = false, readyRef = None, reconnectInfo = None, coloredStackTrace = false, workingDir = workingDir)))

  private var currentId = 0
  private def snippetId = {
    val t = currentId
    currentId += 1
    SnippetId(t.toString, None)
  }
  private var firstRun = true

  private def run(inputs: ScalaCliInputs, allowFailure: Boolean = false)(fish: SnippetProgress => Boolean): Unit = {
    val ip = "my-ip"
    val progressActor = TestProbe()

    scalaCliActor ! ScalaCliActorTask(snippetId, inputs, ip, progressActor.ref)

    val totalTimeout =
      if (firstRun) timeout + 10.second
      else timeout

    progressActor.fishForMessage(totalTimeout + 100.seconds) {
      case progress: SnippetProgress =>
        val fishResult = fish(progress)
        //        println(progress -> fishResult)
        if ((progress.isFailure && !allowFailure) || (progress.isDone && !fishResult))
          throw new Exception(s"Fail to meet expectation at ${progress}")
        else fishResult
    }
    firstRun = false
  }

  private def runCode(code: String, allowFailure: Boolean = false, isWorksheet: Boolean = true)(fish: SnippetProgress => Boolean): Unit = {
    run(ScalaCliInputs.default.copy(code = code, isWorksheetMode = isWorksheet), allowFailure)(fish)
  }

  private def assertUserOutput(
      message: String,
      outputType: ProcessOutputType = ProcessOutputType.StdOut
  )(progress: SnippetProgress): Boolean = {
    val gotHelloMessage = progress.userOutput.exists(out => out.line == message && out.tpe == outputType)
//    if (!gotHelloMessage) assert(progress.userOutput.isEmpty)
    gotHelloMessage
  }

}
