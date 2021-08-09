package com.olegych.scastie.web.oauth2

import com.softwaremill.session.{RefreshTokenData, RefreshTokenStorage, RefreshTokenLookupResult}
import akka.actor.typed.{ActorRef, Behavior, Scheduler, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.mutable

import java.util.UUID

import ActorRefreshTokenStorage.Message

private[oauth2] case class SessionStorage(session: UUID, tokenHash: String, expires: Long)

class ActorRefreshTokenStorage(
  impl: ActorRef[Message]
)(implicit scheduler: Scheduler, ec: ExecutionContext) extends RefreshTokenStorage[UUID] {
  implicit private val timeout = Timeout(10.seconds)

  def lookup(selector: String): Future[Option[RefreshTokenLookupResult[UUID]]] =
    impl.ask(Lookup(_, selector))

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
      case finite: FiniteDuration => scheduler.scheduleOnce(finite, () => op)
      case _: Duration.Infinite   => ()
    }
  }
}

object ActorRefreshTokenStorage {
  sealed trait Message
}
private case class Lookup(
  replyTo: ActorRef[Option[RefreshTokenLookupResult[UUID]]],
  selector: String) extends Message
private case class Store(data: RefreshTokenData[UUID]) extends Message
private case class Remove(selector: String) extends Message

object ActorRefreshTokenStorageImpl {
  private val storage = mutable.Map[String, SessionStorage]()

  def apply(): Behavior[Message] =
    Behaviors
      .supervise[Message](receive)
      .onFailure(SupervisorStrategy.resume)

  private def receive: Behavior[Message] = Behaviors.receiveMessage {
    case Lookup(replyTo, selector) =>
      val lookupResult =
        storage
          .get(selector)
          .map(
            s => RefreshTokenLookupResult(s.tokenHash, s.expires, () => s.session)
          )
      replyTo ! lookupResult
      Behaviors.same
    case Store(data) =>
      storage.put(data.selector, SessionStorage(data.forSession, data.tokenHash, data.expires))
      Behaviors.same
    case Remove(selector) =>
      storage.remove(selector)
      Behaviors.same
  }
}
