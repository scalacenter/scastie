package com.olegych.scastie.web.oauth2

import com.softwaremill.session.{RefreshTokenData, RefreshTokenStorage, RefreshTokenLookupResult}
import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable

import java.util.UUID

private[oauth2] case class SessionStorage(session: UUID, tokenHash: String, expires: Long)

class ActorRefreshTokenStorage(system: ActorSystem) extends RefreshTokenStorage[UUID] {
  import system.dispatcher
  implicit private val timeout = Timeout(10.seconds)
  private val impl = system.actorOf(Props(new ActorRefreshTokenStorageImpl()))

  def lookup(selector: String): Future[Option[RefreshTokenLookupResult[UUID]]] =
    (impl ? Lookup(selector)).mapTo[Option[RefreshTokenLookupResult[UUID]]]

  def store(data: RefreshTokenData[UUID]): Future[Unit] = {
    impl ! Store(data)
    Future.successful(())
  }
  def remove(selector: String): Future[Unit] = {
    impl ! Remove(selector)
    Future.successful(())
  }
  def schedule[S](after: Duration)(op: => Future[S]): Unit = {
    after match {
      case finite: FiniteDuration => system.scheduler.scheduleOnce(finite)(op)
      case _: Duration.Infinite   => ()
    }
  }
}

private[oauth2] case class Lookup(selector: String)
private[oauth2] case class Store(data: RefreshTokenData[UUID])
private[oauth2] case class Remove(selector: String)

class ActorRefreshTokenStorageImpl() extends Actor {
  private val storage = mutable.Map[String, SessionStorage]()
  override def receive: Receive = {
    case Lookup(selector) =>
      val lookupResult =
        storage
          .get(selector)
          .map(
            s => RefreshTokenLookupResult(s.tokenHash, s.expires, () => s.session)
          )
      sender ! lookupResult
    case Store(data) =>
      storage.put(data.selector, SessionStorage(data.forSession, data.tokenHash, data.expires))
    case Remove(selector) =>
      storage.remove(selector)
  }
}
