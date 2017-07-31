package com.olegych.scastie.sbt

import com.olegych.scastie.api
import com.olegych.scastie.api._
import com.olegych.scastie.proto._

import akka.actor.ActorSystem
import akka.remote.serialization.ProtobufSerializer
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._

class SbtActorTest()
    extends TestKit(ActorSystem("SbtActorTest"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  // fails
  test("Simple Instrumentation") {
    sbtRun("1 + 1")(_.instrumentations.nonEmpty)
  }

  test("timeout") {
    sbtRun("while(true){}")(_.timeout)
  }

  // fails
  test("after a timeout the sbt instance is ready to be used") {
    sbtRun("1 + 1")(progress => {
      val gotInstrumentation = progress.instrumentations.nonEmpty

      if (gotInstrumentation) {
        assert(
          progress.instrumentations == List(
            api.Instrumentation(api.Position(0, 5), api.Value("2", "Int")).toProto
          )
        )
      }

      gotInstrumentation
    })
  }

  test("capture runtime errors") {
    sbtRun("1/0")(
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
    sbtRun(s"""println("$message")""")(progress => {
      // we should only receive an hello message
      val gotHelloMessage = progress.userOutput.contains(message)
      if (!gotHelloMessage) assert(progress.userOutput.isEmpty)
      gotHelloMessage
    })
  }

  test("force program mode when an entry point is present") {
    val message = "Hello"
    sbtRun(
      s"""object Main { def main(args: Array[String]): Unit = println("$message") }"""
    ) { progress =>
      assert(progress.forcedProgramMode)

      val gotHelloMessage = progress.userOutput.contains(message)
      if (!gotHelloMessage) assert(progress.userOutput.isEmpty)

      gotHelloMessage
    }
  }

  test("report unsupported dialects") {
    sbtRun("1+1", PlainScala("10.10.10"))(assertCompilationInfo { info =>
      assert(
        info.message == "The worksheet mode does not support this Scala target"
      )
    })
  }

  test("report parsing error") {
    sbtRun("{")(assertCompilationInfo { info =>
      assert(info.message == "} expected but end of file found")
      assert(info.line.contains(1))
    })
  }

  test("Regression #55: Dotty fails to resolve") {
    val dotty = InputsHelper.default.copy(
      code = """|object Main {
                |  def main(args: Array[String]): Unit = {
                |    println("Hello, Dotty!")
                |  }
                |}
                |""".stripMargin,
      target = Dotty.default,
      worksheetMode = false
    )
    sbtRun(dotty)(_.userOutput.contains("Hello, Dotty!"))
  }

  test("Encoding issues #100") {
    sbtRun("""println("€")""") { progress =>
      val gotHelloMessage = progress.userOutput.contains("€")
      if (!gotHelloMessage) assert(progress.userOutput.isEmpty)
      gotHelloMessage
    }
  }

  test("Scala.js support") {
    val scalaJs =
      InputsHelper.default.copy(code = "1 + 1", target = ScalaJs.default)
    sbtRun(scalaJs)(_.done)
  }

  test("Capture System.err #284") {
    val message = "Failure"
    sbtRun(s"""System.err.println("$message")""")(progress => {
      // we should only receive an hello message
      val gotHelloMessage = progress.userOutput == Some(message)
      if (!gotHelloMessage) assert(progress.userOutput.isEmpty)
      gotHelloMessage
    })
  }

  // fails
  test("#258 instrumentation with variable t") {
    sbtRun("val t = 1; t")(_.instrumentations.nonEmpty)
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

  private val timeout = 1.minute
  private val sbtActor = TestActorRef(
    new SbtActor(
      system = system,
      runTimeout = timeout,
      production = false,
      withEnsime = false,
      readyRef = None,
      reconnectInfo = None
    )
  )
  private var currentId = 0
  private def snippetId = {
    val t = currentId
    currentId += 1
    SnippetId(Base64UUID(t.toString), None)
  }
  private var firstRun = true
  private def sbtRun(inputs: Inputs)(fish: SnippetProgress => Boolean): Unit = {
    val ip = "my-ip"
    val progressActor = TestProbe()

    val progressActorPath = 
      ActorRefData(path =
        ProtobufSerializer.serializeActorRef(progressActor.ref).getPath
      )

    sbtActor ! SbtTask(snippetId, inputs, ip, None, progressActorPath)

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
  private def sbtRun(code: String, target: ScalaTarget = PlainScala.default)(
      fish: SnippetProgress => Boolean
  ): Unit = {
    sbtRun(InputsHelper.default.copy(code = code, target = target))(fish)
  }
}
