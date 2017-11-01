package com.olegych.scastie.sbt

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.olegych.scastie.util.SbtTask
import com.olegych.scastie.api._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._

class SbtActorTest()
    extends TestKit(ActorSystem("SbtActorTest"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  print("\u001b")

  (1 to 4).foreach { i =>
    test(s"[$i] timeout") {
      run("while(true){}")(progress => {
        // println(progress)
        progress.isTimeout
      })
    }

    test(s"[$i] after a timeout the sbt instance is ready to be used") {
      run("1 + 1")(progress => {
        val gotInstrumentation = progress.instrumentations.nonEmpty

        if (gotInstrumentation) {
          assert(
            progress.instrumentations == List(
              Instrumentation(Position(0, 5), Value("2", "Int"))
            )
          )
        }

        gotInstrumentation
      })
    }
  }

  test("capture runtime errors") {
    run("1/0")(
      progress => {
        val gotRuntimeError = progress.runtimeError.nonEmpty

        if (gotRuntimeError) {
          val error = progress.runtimeError.get
          assert(error.message == "java.lang.ArithmeticException: / by zero")
          assert(error.line.contains(1))
          assert(error.fullStack.nonEmpty)
        }
        gotRuntimeError
      }
    )
  }

  test("capture user output separately from sbt output") {
    val message = "Hello"
    run(s"""println("$message")""")(
      assertUserOutput(message)
    )
  }

  test("force program mode when an entry point is present") {
    val message = "Hello"
    run(
      s"""object Main { def main(args: Array[String]): Unit = println("$message") }"""
    ) { progress =>
      assert(progress.isForcedProgramMode)
      assertUserOutput(message)(progress)
    }
  }

  test("report parsing error") {
    run("{")(assertCompilationInfo { info =>
      assert(info.message == "} expected but end of file found")
      assert(info.line.contains(1))
    })
  }

  test("report compilation error") {
    run("err")(assertCompilationInfo { info =>
      assert(info.message == "not found: value err")
      assert(info.line.contains(1))
    })
  }

  test("Regression #55: Dotty fails to resolve") {
    val message = "Hello, Dotty!"
    val dotty = Inputs.default.copy(
      code = s"""|object Main {
                 |  def main(args: Array[String]): Unit = {
                 |    println("$message")
                 |  }
                 |}
                 |""".stripMargin,
      target = ScalaTarget.Dotty.default,
      isWorksheetMode = false
    )
    run(dotty)(assertUserOutput("Hello, Dotty!"))
  }

  test("Encoding issues #100") {
    val message = "€"
    run(s"""println("$message")""")(
      assertUserOutput(message)
    )
  }

  test("Disable Predef #357") {
    val message = "No Predef!"
    val input = Inputs.default.copy(
      sbtConfigExtra = "scalacOptions += \"-Yno-predef\" ",
      code = s"""scala.Predef.println("$message")""""
    )
    run(input)(assertUserOutput(message))
  }

  test("Scala.js support") {
    val scalaJs =
      Inputs.default.copy(code = "1 + 1", target = ScalaTarget.Js.default)
    run(scalaJs)(_.isDone)
  }

  test("Capture System.err #284") {
    val message = "Failure"
    run(s"""System.err.println("$message")""")(
      assertUserOutput(message, ProcessOutputType.StdErr)
    )
  }

  test("#258 instrumentation with variable t") {
    run("val t = 1; t")(_.instrumentations.nonEmpty)
  }

  test("#304 null pointer") {
    run("""|trait A {
           |  val a = "1"
           |  val b = a
           |}
           |
           |new A {
           |  override val a = ("2")
           |  println(b)
           |}""".stripMargin)(_.isDone)
  }

  def assertCompilationInfo(
      infoAssert: Problem => Any
  )(progress: SnippetProgress): Boolean = {

    val gotCompilationError = progress.compilationInfos.nonEmpty

    if (gotCompilationError) {
      val info = progress.compilationInfos.head
      infoAssert(info)
    }

    gotCompilationError
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val timeout = 20.seconds

  // SbtProcess uses Stash and it's not compatible with TestActorRef
  // https://stackoverflow.com/questions/18335127/testing-akka-actors-that-mixin-stash-with-testactorref
  private val sbtActor = system.actorOf(
    Props(
      new SbtProcess(
        runTimeout = timeout,
        reloadTimeout = timeout,
        isProduction = false,
        javaOptions = Seq("-Xms512m", "-Xmx1g")
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
  private def run(inputs: Inputs)(fish: SnippetProgress => Boolean): Unit = {
    val ip = "my-ip"
    val progressActor = TestProbe()

    sbtActor ! SbtTask(snippetId, inputs, ip, None, progressActor.ref)

    val totalTimeout =
      if (firstRun) timeout + 10.second
      else timeout

    progressActor.fishForMessage(totalTimeout + 10.seconds) {
      case progress: SnippetProgress =>
        val fishResult = fish(progress)
        if (progress.isDone && !fishResult)
          throw new Exception("Fail to meet expectation")
        else fishResult
    }

    firstRun = false
  }
  private def run(code: String, target: ScalaTarget = ScalaTarget.Jvm.default)(
      fish: SnippetProgress => Boolean
  ): Unit = {
    println("Run: " + code)
    run(Inputs.default.copy(code = code, target = target))(fish)
  }

  private def assertUserOutput(
      message: String,
      outputType: ProcessOutputType = ProcessOutputType.StdOut
  )(progress: SnippetProgress): Boolean = {

    val gotHelloMessage =
      progress.userOutput ==
        Some(ProcessOutput(message, outputType))
    if (!gotHelloMessage) assert(progress.userOutput.isEmpty)

    gotHelloMessage
  }

}
