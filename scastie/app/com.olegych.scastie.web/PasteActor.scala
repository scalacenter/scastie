package com.olegych.scastie
package web

import api._

import scala.collection.JavaConverters._

import play.api.Play
import play.api.Play.{current, configuration}

import akka.actor.{Actor, ActorRef}
import akka.remote.DisassociatedEvent
import akka.routing.{ActorSelectionRoutee, RoundRobinRoutingLogic, Router}

import java.nio.file._
import org.slf4j.LoggerFactory

import scala.collection.mutable.{Map => MMap, Queue}

case class Address(host: String, port: Int)
case class SbtConfig(config: String)
case class InputsWithIp(inputs: Inputs, ip: String)

class PasteActor(progressActor: ActorRef) extends Actor {

  private val portsFrom = configuration.getInt("sbt-remote-ports-from").get
  private val portsSize = configuration.getInt("sbt-remote-ports-size").get
  private val host = configuration.getString("sbt-remote-host").get

  private val ports = (0 until portsSize).map(portsFrom + _)

  private var routees = ports
    .map(
      port =>
        (host, port) -> ActorSelectionRoutee(
          context.actorSelection(
            s"akka.tcp://SbtRemote@$host:$port/user/SbtActor"
          )
      ))
    .toMap

  private var router =
    Router(RoundRobinRoutingLogic(), routees.values.toVector)

  private val log = LoggerFactory.getLogger(getClass)

  override def preStart = {
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    ()
  }

  private val container = new PastesContainer(
    Paths.get(configuration.getString("pastes.data.dir").get)
  )

  private val portsInfo = ports.mkString("[", ", ", "]")
  log.info(s"connecting to: $host $portsInfo")

  def receive = {
    case format: FormatRequest => {
      router.route(format, sender)
      ()
    }
    case InputsWithIp(inputs, ip) => {
      val id = container.writePaste(inputs)
      router.route(SbtTask(id, inputs, ip, progressActor), self)
      sender ! Ressource(id)
    }

    case GetPaste(id) => {
      sender !
        container.readPaste(id).zip(container.readOutput(id)).headOption.map {
          case (inputs, progresses) =>
            FetchResult(inputs, progresses)
        }
    }

    case progress: api.PasteProgress => {
      container.appendOutput(progress)
    }

    case event: DisassociatedEvent => {
      ()
      // for {
      //   host <- event.remoteAddress.host
      //   port <- event.remoteAddress.port
      //   sbt <- sbts.get((host, port))
      // } {
      //   log.warn("removing disconnected: " + sbt)
      //   sbts = sbts - Address(host, port)
      // }
    }
  }
}

case class GetPaste(id: Long)
/*
case class State(sbts: Vector[Sbt],
                 changed: Option[Int] = None,
                 history: Queue[String]) {
  override def toString: String =
    sbts.zipWithIndex
      .map {
        case (c, i) =>
          if (Some(i) == changed) s"*$c*"
          else s" $c "
      }
      .mkString(" ")

  def add(message: String): State = {
    // miss
    def cacheMiss = !sbts.exists(_.sbt == message)

    if(cacheMiss){

    }

    // full
    // hit
  }
}

object Setup {
  util.Random.setSeed(0)

  type Histogram[T] = Map[T, Double]
  def histogram[T](xs: List[T]): Histogram[T] = {
    xs.groupBy(x => x).mapValues(v => (v.size.toDouble / xs.size.toDouble))
  }

  def innerJoin[K, X, Y, Z](m1: Map[K, X], m2: Map[K, Y])(
      f: (X, Y) => Z): Map[K, Z] = {
    m1.flatMap {
      case (k, a) =>
        m2.get(k).map(b => Map(k -> f(a, b))).getOrElse(Map.empty[K, Z])
    }
  }

  def distance[T](h1: Histogram[T], h2: Histogram[T]): Double = {
    innerJoin(h1, h2)((x, y) => (x - y) * (x - y)).values.sum
  }

  def printH[T](h: Histogram[T]): String = {
    h.toList
      .sortBy { case (k, v) => v }
      .reverse
      .map {
        case (k, v) =>
          val v2 = Math.floor(v * 100).toInt
          val v3 = "*" * v2

          s"$k: $v3"
      }
      .mkString(System.lineSeparator)
  }

  val oldMessages =
    util.Random.shuffle(
      List(
        (1 to 10).map(_ => "c1"),
        (1 to 5).map(_ => "c2"),
        (3 to 7).map(i => s"c$i")
      ).flatten)

  val config = Vector(t
    Sbt("c1", 0),
    Sbt("c1", 0),
    Sbt("c2", 0),
    Sbt("c3", 0),
    Sbt("c4", 0)
  )

  // val newMessages =
  //   util.Random.shuffle(List(
  //     (1 to 5).map(_ => "c1"),
  //     (1 to 10).map(_ => "c2"),
  //     (3 to 7).map(i => s"c$i")
  //   ).flatten)
}

case class Sbt(config: String, load: Int) {
  override def toString = s"$config($load)"
}

case class State(sbts: Vector[Sbt],
                 changed: Option[Int] = None,
                 history: Queue[String]) {
  override def toString: String =
    sbts.zipWithIndex
      .map {
        case (c, i) =>
          if (Some(i) == changed) s"*$c*"
          else s" $c "
      }
      .mkString(" ")

  def add(message: String): State = {
    // miss
    def cacheMiss = !sbts.exists(_.sbt == message)

    if(cacheMiss){

    }

    // full
    // hit
  }
}

import Setup._

// printH(histogram(oldMessages))
// printH(histogram(config))

var i = 0
val s = State(config)
var states = List(s)
var lastState = s

// newMessages.map { message =>
//   val i = util.Random.nextInt(lastState.configs.size)
//   val state = State(lastState.configs.updated(i, message), Some(i))

//   lastState = state
//   states = state :: states
// }
//   config.indices
//     .map{i =>
//       val h2 = histogram(config.updated(i, newMessage))
//       val d = Math.floor(distance(h1, h2) * 1000).toInt
//       (i, d, h2)
//     }
//     .mkString(System.lineSeparator)




 */
