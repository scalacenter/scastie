package com.olegych.scastie.client

import play.api.libs.json.{Json, Reads}

import org.scalajs.dom.{
  EventSource,
  WebSocket,
  window,
  CloseEvent,
  Event,
  MessageEvent,
  ErrorEvent
}

import japgolly.scalajs.react.{Callback, CallbackTo}

import scala.util.{Failure, Success}

abstract class EventStream[T: Reads](handler: EventStreamHandler[T]) {
  def onMessage(raw: String): Unit = {
    Json.fromJson[T](Json.parse(raw)).asOpt.foreach { msg =>
      val shouldClose = handler.onMessage(msg)
      if (shouldClose) {
        close()
      }
    }
  }
  def onOpen(): Unit = handler.onOpen()
  def onError(error: String): Unit = handler.onError(error)
  def onClose(reason: Option[String]): Unit = handler.onClose(reason)

  def close(): Unit
}

trait EventStreamHandler[T] {
  def onMessage(msg: T): Boolean
  def onOpen(): Unit
  def onError(error: String): Unit
  def onClose(reason: Option[String]): Unit

  def onErrorC(error: String): Callback
  def onConnected(stream: EventStream[T]): Callback
}

object EventStream {
  def connect[T: Reads](eventSourceUri: String,
                        websocketUri: String,
                        handler: EventStreamHandler[T]): Callback = {

    def connectEventSource =
      CallbackTo[EventStream[T]](
        new EventSourceStream(eventSourceUri, handler)
      )

    def connectWebSocket =
      CallbackTo[EventStream[T]](
        new WebSocketStream(websocketUri, handler)
      )

    connectEventSource.attemptTry.flatMap {
      case Success(eventSource) => handler.onConnected(eventSource)
      case Failure(errorEventSource) => {
        connectWebSocket.attemptTry.flatMap {
          case Success(websocket) => {
            handler.onConnected(websocket)
          }
          case Failure(errorWebSocket) => {
            handler.onErrorC(errorEventSource.toString) >>
              handler.onErrorC(errorWebSocket.toString)
          }
        }
      }
    }
  }
}

class WebSocketStream[T: Reads](uri: String, handler: EventStreamHandler[T])
    extends EventStream[T](handler) {

  private def onOpen(e: Event): Unit = {
    onOpen()
  }

  private def onMessage(e: MessageEvent): Unit = {
    onMessage(e.data.toString)
  }

  private def onError(e: ErrorEvent): Unit = {
    onError(e.message)
  }

  private def onClose(e: CloseEvent): Unit = {
    onClose(Some(e.reason))
  }

  def close(): Unit = {
    onClose(None)
    socket.close()
  }

  val protocol = if (window.location.protocol == "https:") "wss" else "ws"
  val fullUri = s"$protocol://${window.location.host}${uri}"
  val socket = new WebSocket(uri)

  socket.onopen = onOpen _
  socket.onclose = onClose _
  socket.onmessage = onMessage _
  socket.onerror = onError _
}

class EventSourceStream[T: Reads](uri: String, handler: EventStreamHandler[T])
    extends EventStream[T](handler) {

  private def onOpen(e: Event): Unit = {
    onOpen()
  }

  private def onMessage(e: MessageEvent): Unit = {
    onMessage(e.data.toString)
  }

  private def onError(e: Event): Unit = {
    if (e.eventPhase == EventSource.CLOSED) {
      eventSource.close()
      onClose(None)
    } else {
      onError(e.`type`)
    }
  }

  def close(): Unit = {
    onClose(None)
    eventSource.close()
  }

  val eventSource = new EventSource(uri)
  eventSource.onopen = onOpen _
  eventSource.onmessage = onMessage _
  eventSource.onerror = onError _
}
