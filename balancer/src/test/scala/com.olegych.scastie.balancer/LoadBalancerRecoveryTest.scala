package com.olegych.scastie.balancer

import com.olegych.scastie.sbt._
import com.olegych.scastie.api._
import com.olegych.scastie.util.ReconnectInfo

import scala.concurrent._
import scala.concurrent.duration._

import akka.pattern.ask
import akka.util.Timeout
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import com.typesafe.config.{ConfigFactory, Config}

class LoadBalancerRecoveryTest()
    extends TestKit(
      ActorSystem("LoadBalancerRecoveryTest", RemotePortConfig(0))
    )
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  // import system.dispatcher
  implicit val timeout = Timeout(10.seconds)

  test("recover from crash") {
    val crash =
      """|val f = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
         |f.setAccessible(true)
         |val unsafe = f.get(null).asInstanceOf[sun.misc.Unsafe]
         |unsafe.putLong(0, 0)""".stripMargin

    val code1 = "println(1)"
    val code3 = "println(2)"

    val sid1 = run(code1)
    val sid2 = run(crash)
    val sid3 = run(code3)

    val ret = Map(
      sid1 -> code1,
      sid2 -> crash,
      sid3 -> code3
    )

    waitFor(sid1, ret)(_.isDone)
    waitFor(sid2, ret)(_.isTimeout)
    // waitFor(sid2)(_.isDone)
    waitFor(sid3, ret)(_.isDone)
  }

  private val serverAkkaPort = 15000
  private val webSystem = ActorSystem("Web", RemotePortConfig(serverAkkaPort))

  private val sbtAkkaPort = 5150
  private val sbtSystem =
    ActorSystem("SbtRunner", RemotePortConfig(sbtAkkaPort))

  private val progressActor = TestProbe()
  private val statusActor = TestProbe()
  private val sbtActorReadyProbe = TestProbe()

  private val localhost = "127.0.0.1"

  private val sbtActor =
    sbtSystem.actorOf(
      Props(
        new SbtActor(
          system = sbtSystem,
          runTimeout = 10.seconds,
          sbtReloadTimeout = 20.seconds,
          isProduction = false,
          readyRef = Some(sbtActorReadyProbe.ref),
          reconnectInfo = Some(
            ReconnectInfo(
              serverHostname = localhost,
              serverAkkaPort = serverAkkaPort,
              actorHostname = localhost,
              actorAkkaPort = sbtAkkaPort
            )
          )
        )
      ),
      name = "SbtActor"
    )

  sbtActorReadyProbe.fishForMessage(60.seconds) {
    case SbtActorReady => {
      println("sbt ready")
      true
    }
    case msg => {
      println("***")
      println(msg)
      println("***")
      false
    }
  }

  private val dispatchActor =
    webSystem.actorOf(
      Props(new DispatchActor(progressActor.ref, statusActor.ref)),
      name = "DispatchActor"
    )

  private var id = 0
  private def run(code: String): SnippetId = {
    val wrapped =
      s"""|object Main {
          |  def main(args: Array[String]): Unit = {
          |    $code
          |  }
          |}""".stripMargin

    val inputs = Inputs.default.copy(code = wrapped, isWorksheetMode = false)

    val task = RunSnippet(
      InputsWithIpAndUser(inputs, UserTrace("ip-" + id, None))
    )

    id += 1

    Await.result(
      (dispatchActor.ask(task)).mapTo[SnippetId],
      10.seconds
    )
  }

  private def waitFor(sid: SnippetId, ret: Map[SnippetId, String])(
      f: SnippetProgress => Boolean
  ): Unit = {

    progressActor.fishForMessage(30.seconds) {
      case progress: SnippetProgress => {
        if (progress.snippetId.get != sid) {
          println()
          println()
          println("*******************************")
          println("expected: " + ret(sid))
          println("got: " + ret(progress.snippetId.get))
          println("*******************************")
          println()
          println()

          assert(false)
        }

        f(progress)
      }
    }
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(webSystem)
    TestKit.shutdownActorSystem(sbtSystem)
    TestKit.shutdownActorSystem(system)
  }
}

object RemotePortConfig {
  def apply(port: Int): Config =
    ConfigFactory.parseString(
      s"""|akka {
          |  actor {
          |    provider = "akka.remote.RemoteActorRefProvider"
          |  }
          |  remote {
          |    netty.tcp {
          |      hostname = "127.0.0.1"
          |      port = $port
          |    }
          |  }
          |}""".stripMargin
    )
}
