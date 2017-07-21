package com.olegych.scastie.sbt

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.olegych.scastie.SbtTask
import com.olegych.scastie.api._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._

class SbtActorTest()
    extends TestKit(ActorSystem("SbtActorTest"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  test("Simple Instrumentation") {
    run("1 + 1")(_.instrumentations.nonEmpty)
  }

  test("timeout") {
    run("while(true){}")(_.timeout)
  }

  test("after a timeout the sbt instance is ready to be used") {
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

  test("capture runtime errors") {
    run("1/0")(
      progress => {
        val gotRuntimeError = progress.runtimeError.nonEmpty

        if (gotRuntimeError) {
          val error = progress.runtimeError.get
          assert(error.message == "java.lang.ArithmeticException: / by zero")
          assert(error.line == Some(1))
          assert(error.fullStack.nonEmpty)
        }
        gotRuntimeError
      }
    )
  }

  test("capture user output separately from sbt output") {
    val message = "Hello"
    run(s"""println("$message")""")(progress => {
      // we should only receive an hello message
      val gotHelloMessage = progress.userOutput == Some(message)
      if (!gotHelloMessage) assert(progress.userOutput.isEmpty)
      gotHelloMessage
    })
  }

  test("force program mode when an entry point is present") {
    val message = "Hello"
    run(
      s"""object Main { def main(args: Array[String]): Unit = println("$message") }"""
    ) { progress =>
      assert(progress.forcedProgramMode)

      val gotHelloMessage = progress.userOutput == Some(message)
      if (!gotHelloMessage) assert(progress.userOutput.isEmpty)

      gotHelloMessage
    }
  }

  test("report unsupported dialects") {
    run("1+1", ScalaTarget.Jvm("10.10.10"))(assertCompilationInfo { info =>
      assert(
        info.message == "The worksheet mode does not support this Scala target"
      )
    })
  }

  test("report parsing error") {
    run("{")(assertCompilationInfo { info =>
      assert(info.message == "} expected but end of file found")
      assert(info.line == Some(1))
    })
  }

  test("Regression #55: Dotty fails to resolve") {
    val dotty = Inputs.default.copy(
      code = """|object Main {
                |  def main(args: Array[String]): Unit = {
                |    println("Hello, Dotty!")
                |  }
                |}
                |""".stripMargin,
      target = ScalaTarget.Dotty,
      worksheetMode = false
    )
    run(dotty)(_.userOutput.contains("Hello, Dotty!"))
  }

  test("Encoding issues #100") {
    run("""println("€")""") { progress =>
      val gotHelloMessage = progress.userOutput == Some("€")
      if (!gotHelloMessage) assert(progress.userOutput.isEmpty)
      gotHelloMessage
    }
  }

  test("Scala.js support") {
    val scalaJs =
      Inputs.default.copy(code = "1 + 1", target = ScalaTarget.Js.default)
    run(scalaJs)(_.done)
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

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  private val timeout = 20.seconds
  private val sbtActor = TestActorRef(
    new SbtActor(
      system = system,
      runTimeout = timeout,
      production = false,
      withEnsime = false,
      readyRef = None
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
      if (firstRun) timeout + 20.second
      else timeout

    progressActor.fishForMessage(totalTimeout + 5.seconds) {
      case progress: SnippetProgress =>
        val fishResult = fish(progress)
        if (progress.done && !fishResult)
          throw new Exception("Fail to meet expectation")
        else fishResult
    }

    firstRun = false
  }
  private def run(code: String, target: ScalaTarget = ScalaTarget.Jvm.default)(
      fish: SnippetProgress => Boolean
  ): Unit = {
    run(Inputs.default.copy(code = code, target = target))(fish)
  }
}
