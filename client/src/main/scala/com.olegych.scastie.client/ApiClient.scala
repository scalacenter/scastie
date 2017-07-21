package com.olegych.scastie.client

import org.scalajs.dom
import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import upickle.default.{Reader, Writer, write => uwrite, read => uread}

object ApiClient extends autowire.Client[String, Reader, Writer] {
  override def doCall(req: Request): Future[String] = {
    dom.ext.Ajax
      .post(
        url = "/api/" + req.path.mkString("/"),
        data = write(req.args)
      )
      .map(_.responseText)
  }

  def read[T: Reader](p: String): T = uread[T](p)
  def write[T: Writer](r: T): String = uwrite(r)
}
