package com.olegych.scastie.web

import com.olegych.scastie.proto._

import com.google.protobuf.{ByteString, CodedInputStream}
import com.trueaccord.scalapb.GeneratedMessage

private[web] trait ProtobufLookup[T] {
  def parseFrom(input: CodedInputStream): T
}

private[web] trait ProtobufWrite[T] {
  def toByteString(msg: T): ByteString
}

object AutowireServer extends autowire.Server[ByteString, ProtobufLookup, ProtobufWrite] {

  private implicit val writer = new ProtobufWrite[GeneratedMessage] {
    def toByteString(msg: GeneratedMessage): ByteString = msg.toByteString
  }

  private implicit val inputsProto = new ProtobufLookup[Inputs]{
    def parseFrom(input: CodedInputStream) = Inputs.parseFrom(input)
  }

  private implicit val snippetIdProto = new ProtobufLookup[SnippetId]{
    def parseFrom(input: CodedInputStream) = SnippetId.parseFrom(input)
  }

  private implicit val formatRequestProto = new ProtobufLookup[FormatRequest]{
    def parseFrom(input: CodedInputStream) = FormatRequest.parseFrom(input)
  }

  private implicit val oldIdProto = new ProtobufLookup[OldId]{
    def parseFrom(input: CodedInputStream) = OldId.parseFrom(input)
  }

  private implicit val ensimeProto = new ProtobufLookup[EnsimeRequest]{
    def parseFrom(input: CodedInputStream) = EnsimeRequest.parseFrom(input)
  }

  def read[T: ProtobufLookup](bs: ByteString): T = {
    implicitly[ProtobufLookup[T]].parseFrom(bs.newCodedInput())
  }

  def write[T: ProtobufWrite](msg: T): ByteString = {
    implicitly[ProtobufWrite[T]].toByteString(msg)
  }
}