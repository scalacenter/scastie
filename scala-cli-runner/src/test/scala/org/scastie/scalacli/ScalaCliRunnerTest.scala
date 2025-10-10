package org.scastie.scalacli

import java.nio.file.Files
import java.nio.file.Paths
import scala.concurrent.duration._
import scala.concurrent.Future

import org.scastie.api._
import org.scastie.api.SnippetId
import org.scastie.runtime.api._
import org.scastie.util.RunnerTerminated
import org.scastie.util.SbtTask
import org.scastie.util.ScalaCliActorTask
import org.scastie.util.ScastieFileUtil
import org.scastie.util.StopRunner

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.testkit.TestActor.AutoPilot
import akka.testkit.TestActorRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.BeforeAndAfterAll

class ScalaCliRunnerTest
  extends TestKit(ActorSystem("ScalaCliRunnerTest"))
  with ImplicitSender
  with AnyFunSuiteLike
  with BeforeAndAfterAll {
  val workingDir = Files.createTempDirectory("scastie")
  println(workingDir)

  setAutoPilot(new AutoPilot {
    def run(sender: ActorRef, msg: Any): AutoPilot = {
      sender ! s"reply to $msg"
      this
    }
  })
  // print("\u001b")

  (1 to 2).foreach { i =>
    test(s"[$i] timeout") {
      runCode(s"Thread.sleep(${timeout.toMillis + 3000})", allowFailure = true)(progress => {
        progress.isTimeout
      })
    }

    test(s"[$i] after a timeout the scala-cli instance is ready to be used") {
      runCode("1 + 1")(progress => {
        val gotInstrumentation = progress.instrumentations.nonEmpty

        if (gotInstrumentation) {
          assert(
            progress.instrumentations == List(
              Instrumentation(Position(0, 5), Value("2", "scala.Int"))
            )
          )
        }

        gotInstrumentation
      })
    }
  }

  test("capture runtime errors") {
    runCode("1/0", allowFailure = true) { progress =>
      progress.runtimeError.forall { error =>
        error.message.nonEmpty && error.message.contains("java.lang.ArithmeticException: / by zero")
      }
    }
  }

  test("trap sys.exit") {
    runCode("sys.exit(2)", allowFailure = false) { progress =>
      progress.buildOutput.exists(_.line.contains("Process exited with error code 2"))
    }
  }

  test("capture user output separately from build output") {
    val message = "Hello"
    runCode(s"""println("$message")""")(
      assertUserOutput(message)
    )
  }

  test("report parsing error") {
    runCode("\n4444444444444444444\n", allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "number too large")
    })
  }

  test("report compilation error") {
    runCode("err", allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "Not found: err")
      assert(info.line.contains(1))
    })
  }

  test("Encoding issues #100") {
    val message = "€"
    runCode(s"""println("$message")""")(
      assertUserOutput(message)
    )
  }

  test("Disable Predef #357") {
    val message = "No Predef!"
    val testCode = s"""//> using option -Yno-predef
                      |scala.Predef.println("$message")""".stripMargin
    val verificationCode = s"""//> using option -Yno-predef
                              |println("$message")""".stripMargin

    runCode(testCode)(assertUserOutput(message))
    runCode(verificationCode, allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "Not found: println")
    })
  }

  test("Scala 2.12 support") {
    val code = s"""//> using scala 2.12
                  |object Main extends App {
                  |  println(util.Properties.versionNumberString)
                  |}""".stripMargin
    runCode(code, isWorksheet = false)(assertUserOutput(output => assert(output.line.startsWith("2.12"))))
  }

  test("Scala 2.13 support") {
    val code = s"""//> using scala 2.13
                  |object Main extends App {
                  |  println(util.Properties.versionNumberString)
                  |}""".stripMargin

    val verificationCode = s"""//> using scala 2.13
                              |@main def hello = println("?")""".stripMargin

    runCode(code, isWorksheet = false)(assertUserOutput(output => assert(output.line.startsWith("2.13"))))
    runCode(verificationCode, allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "not found: type main")
    })
  }

  test("Scala 3.0 support") {
    val code = s"""//> using scala 3
                  |@main def hello = println(1 + 1)""".stripMargin
    runCode(code, isWorksheet = false)(assertUserOutput("2"))
  }

  test("Scala 3 support") {
    val code = s"""//> using scala 3
                  |@main def hello = println(1 + 1)""".stripMargin
    runCode(code, isWorksheet = false)(assertUserOutput("2"))
  }

  test("Scala 2.12 worksheet support") {
    val code = s"""//> using scala 2.12
                  |println(util.Properties.versionNumberString)""".stripMargin
    runCode(code)(assertUserOutput(output => assert(output.line.startsWith("2.12"))))
  }

  test("Scala 2.13 worksheet support") {
    val code = s"""//> using scala 2.13
                  |println(util.Properties.versionNumberString)""".stripMargin

    val verificationCode = s"""//> using scala 2.13
                              |@main def hello = println("?")""".stripMargin

    runCode(code)(assertUserOutput(output => assert(output.line.startsWith("2.13"))))
    runCode(verificationCode, allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "not found: type main")
    })
  }

  test("Scala 3.0 worksheet support") {
    val code = s"""//> using scala 3.0
                  |if true then println(1 + 1)""".stripMargin
    runCode(code)(assertUserOutput("2"))
  }

  test("Scala 3 worksheet support") {
    val code = s"""//> using scala 3
                  |if true then println(1 + 1)""".stripMargin
    runCode(code)(assertUserOutput("2"))
  }

  test("avoid https://github.com/scala/bug/issues/8119") {
    val code = s"""//> using scala 2.12
                  |val n = 0; val m = List(1).par.foreach(_ => n); println(1)""".stripMargin
    runCode(code)(assertUserOutput("1"))
  }

  test("no warnings on scala 3") {
    val code = s"""//> using option -Xfatal-warnings
                  |//> using scala 3
                  |println(1 + 1)""".stripMargin
    runCode(code)(progress => progress.compilationInfos.isEmpty)
  }

  test("no warnings on 2.13") {
    val code = s"""//> using options -Xfatal-warnings -Xlint
                  |//> using scala 2.13
                  |println(1 + 1)""".stripMargin
    runCode(code)(progress => progress.compilationInfos.isEmpty)
  }

  test("no warnings on 2.12") {
    val code = s"""//> using options -Xfatal-warnings -Xlint
                  |//> using scala 2.12
                  |println(1 + 1)""".stripMargin
    runCode(code)(progress => progress.compilationInfos.isEmpty)
  }

  test("JVM 8") {
    val code = s"""//> using jvm 8
                  |println(1 + 1)""".stripMargin
    runCode(code)(assertUserOutput("2"))
  }

  test("JVM 11") {
    val code = s"""//> using jvm 11
                  |println(1 + 1)""".stripMargin
    runCode(code)(assertUserOutput("2"))
  }

  test("JVM 17") {
    val code = s"""//> using jvm 17
                  |println(1 + 1)""".stripMargin
    runCode(code)(assertUserOutput("2"))
  }

  test("JVM 21") {
    val code = s"""//> using jvm 21
                  |println(1 + 1)""".stripMargin
    runCode(code)(assertUserOutput("2"))
  }

  test("Capture System.err #284") {
    val message = "Failure"
    runCode(s"""System.err.println("$message")""")(
      assertUserOutput(message, ProcessOutputType.StdErr)
    )
  }

  test("#258 instrumentation with variable t") {
    runCode("val t = 1; t")(_.instrumentations.nonEmpty)
  }

  test("last line comment should not fail compilation in worksheet Scala 2") {
    val code = s"""//> using scala 2
                  |println("Hello world!")
                  |// test comment""".stripMargin
    runCode(code)(assertUserOutput("Hello world!"))
  }

  test("last line comment should not fail compilation in worksheet Scala 3") {
    val code = s"""//> using scala 3
                  |println("Hello world!")
                  |// test comment""".stripMargin
    runCode(code)(assertUserOutput("Hello world!"))
  }

  test("hide Playground from types") {
    runCode("case class A(i:Int) extends AnyVal; A(1)")(
      _.instrumentations.headOption.exists(_.render == Value("A(1)", "A"))
    )
  }

  test("#304 null pointer") {
    runCode(
      """|trait A {
         |  val a = "1"
         |  val b = a
         |}
         |
         |new A {
         |  override val a = ("2")
         |  println(b)
         |}""".stripMargin,
      allowFailure = true
    )(_.isDone)
  }

  test("Scala-CLI worksheet correct error position") {
    val code = s"""
                  |
                  |printl
                  |
                  |""".stripMargin

    runCode(code, allowFailure = true, isWorksheet = true)(assertCompilationInfo { info =>
      assert(info.message == "Not found: printl - did you mean print? or perhaps printf or println?")
      assert(info.line == Some(3)) // error lines are 1-based
    })

  }

  test("Scala-CLI non-worksheet correct error position") {
    val code = s"""
                  |
                  |printl
                  |
                  |""".stripMargin

    runCode(code, allowFailure = true, isWorksheet = false)(assertCompilationInfo { info =>
      assert(info.message == "Illegal start of toplevel definition")
      assert(info.line == Some(3)) // error lines are 1 based
    })
  }

  private val macroCode = """import scala.quoted._
                            |
                            |object SleepMacro:
                            |  inline def sleep(inline time: Int) =
                            |    ${ wait('time) }
                            |
                            |  def wait(x: Expr[Int])(using Quotes): Expr[Any] =
                            |    Thread.sleep(x.valueOrAbort)
                            |    x
                            |""".stripMargin

  test("No bsp timeout") {
    Files.writeString(workingDir.resolve("SleepMacro.scala"), macroCode)
    runCode(longCompilation(1000), isWorksheet = false)(assertUserOutput("test"))
    Files.delete(workingDir.resolve("SleepMacro.scala"))
  }

  test("BSP Timeout") {
    Files.writeString(workingDir.resolve("SleepMacro.scala"), macroCode)
    runCode(longCompilation(compilationTimeout.toMillis + 5000), isWorksheet = false, allowFailure = true)(
      assertCompilationInfo { info =>
        assert(info.message == "Build Server Timeout Exception")
      }
    )
    Files.delete(workingDir.resolve("SleepMacro.scala"))
  }

  def longCompilation(time: Long): String = s"""//> using scala 3
                                               |//> using file SleepMacro.scala
                                               |
                                               |@main def hello =
                                               |  SleepMacro.sleep($time)
                                               |  println("test")
                                               |""".stripMargin

  test("BSP Timeout Multiple snippets") {
    Files.writeString(workingDir.resolve("SleepMacro.scala"), macroCode)
    runCode(longCompilation(compilationTimeout.toMillis + 5000), isWorksheet = false, allowFailure = true)(
      assertCompilationInfo { info =>
        assert(info.message == "Build Server Timeout Exception")
      }
    )
    runCode(longCompilation(100), isWorksheet = false, allowFailure = false)(assertUserOutput("test"))

    runCode(longCompilation(compilationTimeout.toMillis + 5000), isWorksheet = false, allowFailure = true)(
      assertCompilationInfo { info =>
        assert(info.message == "Build Server Timeout Exception")
      }
    )

    runCode(longCompilation(100), isWorksheet = false, allowFailure = false)(assertUserOutput("test"))
    Files.delete(workingDir.resolve("SleepMacro.scala"))
  }

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
    println("Cleaning processess")
    scalaCliActor ! StopRunner

    fishForMessage(1.minute, "RunnerTerminated") {
      case RunnerTerminated => true
      case _                => false
    }

    TestKit.shutdownActorSystem(system, 1.minute, true)
  }

  private val timeout = 45.seconds
  private val compilationTimeout = 25.seconds

  val scalaCliActor = system.actorOf(
    Props(
      new ScalaCliActor(
        runTimeout = timeout,
        isProduction = false,
        reconnectInfo = None,
        coloredStackTrace = false,
        compilationTimeout = compilationTimeout,
        workingDir = workingDir
      )
    )
  )

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

    progressActor.fishForMessage(totalTimeout + 100.seconds) { case progress: SnippetProgress =>
      val fishResult = fish(progress)
      //        println(progress -> fishResult)
      if ((progress.isFailure && !allowFailure) || (progress.isDone && !fishResult))
        throw new Exception(s"Fail to meet expectation at ${progress}")
      else fishResult
    }
    firstRun = false
  }

  private def runCode(code: String, allowFailure: Boolean = false, isWorksheet: Boolean = true)(
    fish: SnippetProgress => Boolean
  ): Unit = {
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
