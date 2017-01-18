package com.olegych.scastie
package sbt

import api._

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, ImplicitSender, TestProbe, TestActorRef}
import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}

import scala.concurrent.duration._

class SbtActorTest()
    extends TestKit(ActorSystem("SbtActorTest"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  val progressActor = TestProbe()

  val timeout  = 3.seconds
  val sbtActor = TestActorRef(new SbtActor(timeout))

  test("timeout") {
    val id           = 0L
    val infiniteLoop = Inputs.default.copy(
      code = """|class Playground {
                |  while(true){}
                |}""".stripMargin
    )
    sbtActor ! SbtTask(id, infiniteLoop, progressActor.ref)

    progressActor.fishForMessage(timeout + 20.second) {
      case progress: PasteProgress => {
        progress.timeout
      }
    }
  }

  test("after a timeout the sbt instance is ready to be used") {

    val helloWorld = Inputs.default.copy(code = "class Playground { 1 + 1 }")
    sbtActor ! SbtTask(1L, helloWorld, progressActor.ref)

    // val sbtReloadTime = 30.seconds

    progressActor.fishForMessage(timeout + 20.second) {
      case progress: PasteProgress => {
        progress.instrumentations.nonEmpty
      }
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
