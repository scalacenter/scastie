package org.scastie.client

import io.circe._
import io.circe.parser._
import io.circe.syntax._

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.CallbackTo
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.Event
import org.scalajs.dom.EventSource
import org.scalajs.dom.MessageEvent
import org.scalajs.dom.WebSocket
import org.scalajs.dom.window

import scala.util.Failure
import scala.util.Success

abstract class EventStream[T: Decoder](handler: EventStreamHandler[T]) {
  var closing = false

  def onMessage(raw: String): Unit = {
    if (!closing) {
      decode[T](raw).toOption.foreach { msg =>
        val shouldClose = handler.onMessage(msg)
        if (shouldClose) {
          close()
        }
      }
    }
  }
  def onOpen(): Unit = handler.onOpen()
  def onError(error: String): Unit = handler.onError(error)
  def onClose(reason: Option[String]): Unit = handler.onClose(reason)

  def close(force: Boolean = false): Unit = {
    closing = true
    if (!force) {
      onClose(None)
    }
  }
}

trait EventStreamHandler[T] {
  def onMessage(msg: T): Boolean
  def onOpen(): Unit
  def onError(error: String): Unit
  def onClose(reason: Option[String]): Unit

  def onConnectionError(error: String): Callback
  def onConnected(stream: EventStream[T]): Callback
}

object EventStream {
  def connect[T: Decoder](eventSourceUri: String, websocketUri: String, handler: EventStreamHandler[T]): Callback = {

    def connectEventSource =
      CallbackTo[EventStream[T]](
        new EventSourceStream(eventSourceUri, handler)
      )

    def connectWebSocket =
      CallbackTo[EventStream[T]](
        new WebSocketStream(websocketUri, handler)
      )

    connectEventSource.attemptTry.flatMap {
      case Success(eventSource) => {
        handler.onConnected(eventSource)
      }
      case Failure(errorEventSource) => {
        connectWebSocket.attemptTry.flatMap {
          case Success(websocket) => {
            handler.onConnected(websocket)
          }
          case Failure(errorWebSocket) => {
            handler.onConnectionError(errorEventSource.toString) >>
              handler.onConnectionError(errorWebSocket.toString)
          }
        }
      }
    }
  }
}

class WebSocketStream[T: Decoder](uri: String, handler: EventStreamHandler[T]) extends EventStream[T](handler) {

  private def onOpen(e: Event): Unit = {
    onOpen()
  }

  private def onMessage(e: MessageEvent): Unit = {
    onMessage(e.data.toString)
  }

  private def onClose(e: CloseEvent): Unit = {
    onClose(Some(e.reason))
  }

  override def close(force: Boolean = false): Unit = {
    super.close(force)
    socket.close()
  }

  val protocol: String =
    if (window.location.protocol == "https:") "wss" else "ws"
  val fullUri: String = s"$protocol://${window.location.host}${uri}"
  val socket: WebSocket = new WebSocket(uri)

  socket.onopen = e => onOpen(e)
  socket.onclose = e => onClose(e)
  socket.onmessage = e => onMessage(e)
}

class EventSourceStream[T: Decoder](uri: String, handler: EventStreamHandler[T]) extends EventStream[T](handler) {

  private def onOpen(e: Event): Unit = {
    onOpen()
  }

  private def onMessage(e: MessageEvent): Unit = {
    try {
      onMessage(e.data.toString)
    } catch {
      case error: Throwable =>
        error.printStackTrace()
        println(e)
        println(e.data)
    }
  }

  private def onError(e: Event): Unit = {
    if (e.eventPhase == EventSource.CLOSED) {
      eventSource.close()
      onClose(None)
    } else {
      onError(e.`type`)
    }
  }

  override def close(force: Boolean = false): Unit = {
    super.close(force)
    eventSource.close()
  }

  val eventSource: EventSource = new EventSource(uri)
  eventSource.onopen = e => onOpen(e)
  eventSource.onmessage = e => onMessage(e)
  eventSource.onerror = e => onError(e)
}
