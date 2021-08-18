package com.olegych.scastie.balancer

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.testkit.typed.FishingOutcome
import com.typesafe.sslconfig.util.EnrichedConfig
import akka.util.Timeout
import com.olegych.scastie.api._
import com.olegych.scastie.sbt._
import com.olegych.scastie.util.Services
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent._
import scala.concurrent.duration._

class LoadBalancerRecoveryTest()
    extends AnyFunSuiteLike
    with BeforeAndAfterAll {

  implicit val timeout = Timeout(10.seconds)

  test("recover from crash") {
    val crash =
      """|val f = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
         |f.setAccessible(true)
         |val unsafe = f.get(null).asInstanceOf[sun.misc.Unsafe]
         |println("TRYING TO CRASH JVM")
         |unsafe.putLong(0, 0)
         |println("SHOULD HAVE CRASHED!")""".stripMargin

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
    waitFor(sid3, ret)(_.isDone)
  }

  private val config = EnrichedConfig(
    ConfigFactory.load().getConfig("com.olegych.scastie")
  )
  private val testingConfig = TestingConfig(config.get[String]("system-name"))
  import testingConfig._

  private val sbtSystem = ActorSystem(
    SbtActor(config.get[SbtConf]("sbt")),
    systemName,
    testingConfig(sbtAkkaPort)
  )

  object WebSystem {
    sealed trait Message
    case class AskProgressActor(replyTo: ActorRef[TestProbe[ProgressActor.Message]]) extends Message

    def apply(): Behavior[Message] =
      Behaviors.setup { context =>
        // progressActor and statusActor need be in same system as DispatchActor
        // otherwise, akka will complain about serializing ActerRef
        implicit def system: ActorSystem[_] = context.system
        val progressActor = TestProbe[ProgressActor.Message]()
        val statusActor = TestProbe[StatusActor.Message]()
        /* val dispatchActor = */ context.spawn(
          DispatchActor(
            progressActor.ref,
            statusActor.ref,
            config.get[BalancerConf]("balancer"),
          ),
          "DispatchActor"
        )
        Behaviors.receiveMessage {
          case AskProgressActor(replyTo) =>
            replyTo ! progressActor
            Behaviors.same
        }
      }
  }

  private val webSystem = ActorSystem(
    WebSystem(),
    systemName,
    testingConfig(serverAkkaPort)
  )

  private val testKit = ActorTestKit(webSystem)
  private val runnersReadyProbe = testKit.createTestProbe[Receptionist.Listing]()
  testKit.system.receptionist ! Receptionist.Subscribe(
    Services.Balancer,
    runnersReadyProbe.ref
  )

  // wait sbt runners available in dispatchActor before sending RunSnippet to it
  private val dispatchActor =
    runnersReadyProbe.fishForMessage(60.seconds) {
      case Services.Balancer.Listing(listings) =>
        if (listings.isEmpty) {
          FishingOutcome.ContinueAndIgnore
        } else {
          println("runners are ready")
          FishingOutcome.Complete
        }
    }.head match {
      case Services.Balancer.Listing(refs) => refs.head
    }

  implicit val scheduler: Scheduler = webSystem.scheduler
  private var id = 0
  private def run(code: String): SnippetId = {
    val wrapped =
      s"""|object Main {
          |  def main(args: Array[String]): Unit = {
          |    $code
          |  }
          |}""".stripMargin

    val inputs = Inputs.default.copy(code = wrapped, _isWorksheetMode = false)

    val taskAsk = dispatchActor.ask(
      RunSnippet(_, InputsWithIpAndUser(inputs, UserTrace("ip-" + id, None)))
    )

    id += 1

    Await.result(taskAsk, 10.seconds)
  }

  import WebSystem._
  private val progressActor = Await.result(webSystem.ask(AskProgressActor), 10.seconds)

  private def waitFor(sid: SnippetId, ret: Map[SnippetId, String])(
      f: SnippetProgress => Boolean
  ) =
    progressActor.fishForMessagePF(50.seconds) {
      case progress: SnippetProgress =>
        if (progress.snippetId.get != sid) {
          FishingOutcome.Fail(s"""\n
           |*******************************
           |expected: ${ret(sid)}
           |got: ${ret(progress.snippetId.get)}
           |*******************************\n\n""".stripMargin)
        } else if (f(progress)) {
          FishingOutcome.Complete
        } else {
          FishingOutcome.ContinueAndIgnore
        }
    }

  override def afterAll(): Unit = {
    ActorTestKit.shutdown(webSystem)
    ActorTestKit.shutdown(sbtSystem)
  }
}

private case class TestingConfig(systemName: String) {
  val serverAkkaPort = 15000
  val sbtAkkaPort = 5150

  def apply(port: Int): Config =
    ConfigFactory.parseString(
      s"""|akka {
          |  remote {
          |    artery.canonical {
          |      hostname = "127.0.0.1"
          |      port = $port
          |    }
          |  }
          |  cluster {
          |    seed-nodes = [
          |      "akka://$systemName@127.0.0.1:$serverAkkaPort",
          |      "akka://$systemName@127.0.0.1:$sbtAkkaPort"]
          |    jmx.enabled = off
          |  }
          |}
          |com.olegych.scastie.sbt {
          |  run-timeout = 10s
          |  sbtReloadTimeout = 20s
          |}
          |""".stripMargin
    )
}
