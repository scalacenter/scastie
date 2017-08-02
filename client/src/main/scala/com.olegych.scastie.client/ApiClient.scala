package com.olegych.scastie.client

import org.scalajs.dom
import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import java.nio.ByteBuffer

import com.olegych.scastie.proto._

import com.google.protobuf.{ByteString, CodedInputStream}
import com.trueaccord.scalapb.GeneratedMessage

private[client] trait ProtobufLookup[T] {
  def parseFrom(input: CodedInputStream): T
}

private[client] trait ProtobufWrite[T] {
  def toByteString(msg: T): ByteString
}

object ApiClient extends autowire.Client[ByteString, ProtobufLookup, ProtobufWrite] {
  private implicit def writer[T <: GeneratedMessage] = 
    new ProtobufWrite[T] {
      def toByteString(msg: T): ByteString = msg.toByteString
    }

  override def doCall(req: Request): Future[String] = {
    dom.ext.Ajax
      .post(
        url = "/api/" + req.path.mkString("/"),
        data = 
          toByteBuffer(
            write(
              AutowireArgs(args = req.args)
            )
          )
      )
      .map(_.responseText)
  }


  def toByteBuffer(bs: ByteString): ByteBuffer = {
    ByteBuffer.wrap(bs.toByteArray).asReadOnlyBuffer()
  }

  def read[T: ProtobufLookup](bs: ByteString): T = {
    implicitly[ProtobufLookup[T]].parseFrom(bs.newCodedInput())
  }

  def write[T: ProtobufWrite](msg: T): ByteString = {
    implicitly[ProtobufWrite[T]].toByteString(msg)
  }
}
