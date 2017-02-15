package com.olegych.scastie
package sbt

import api._

import akka.actor.ActorSystem
import akka.testkit.{TestKit, ImplicitSender, TestProbe, TestActorRef}
import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}

import System.{lineSeparator => nl}

import scala.concurrent.duration._

class SbtActorTest()
    extends TestKit(ActorSystem("SbtActorTest"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  test("timeout") {
    run("while(true){}", _.timeout)
  }

  test("after a timeout the sbt instance is ready to be used") {
    run("1 + 1", progress => {
      val gotInstrumentation = progress.instrumentations.nonEmpty

      if (gotInstrumentation) {
        val instrumentations = progress.instrumentations.head
        assert(
          progress.instrumentations == List(
            Instrumentation(Position(0, 5), Value("2", "Int"))
          ))
      }

      gotInstrumentation
    })
  }

  test("capture runtime errors") {
    run("1/0", progress => {
      val gotRuntimeError = progress.runtimeError.nonEmpty

      if (gotRuntimeError) {
        val error = progress.runtimeError.get
        println(error)
        assert(error.message == "java.lang.ArithmeticException: / by zero")
        assert(error.line == Some(1))
        assert(error.fullStack.size > 0)
      }
      gotRuntimeError
    })
  }

  test("capture user output separately from sbt output") {
    val message = "Hello"
    run(s"""println("$message")""", progress => {
      // we should only receive an hello message
      val gotHelloMessage = progress.userOutput == Some(message + nl)
      if (!gotHelloMessage) assert(progress.userOutput == None)
      gotHelloMessage
    })
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  private val timeout = 3.seconds
  private val sbtActor = TestActorRef(
    new SbtActor(timeout, production = false))
  private var currentId = 0
  private def id = {
    val t = currentId
    currentId += 1
    t
  }
  private var firstRun = true
  private def run(code: String, fish: PasteProgress => Boolean): Unit = {
    val ip = "my-ip"
    val login = "github-login"
    val progressActor = TestProbe()

    val inputs = Inputs.default.copy(code = code)
    sbtActor ! SbtTask(id, inputs, ip, login, progressActor.ref)

    val totalTimeout =
      if (firstRun) timeout + 10.second
      else timeout

    progressActor.fishForMessage(totalTimeout) {
      case progress: PasteProgress => fish(progress)
    }

    firstRun = false
  }
}
