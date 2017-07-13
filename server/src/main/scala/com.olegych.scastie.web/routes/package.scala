package com.olegych.scastie
package web

import scala.io.Source
import System.{lineSeparator => nl}

import akka.http.scaladsl.model._
import StatusCodes.NotFound

package object routes {
  def getResource(path: String): Option[String] = {
    Option(getClass.getResourceAsStream(path)).map { stream =>
      val source = Source.fromInputStream(stream)
      val content = source.getLines.mkString(nl)
      source.close()
      content
    }
  }

  private def html(content: String) =
    HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, content))

  def serveStatic(content: Option[String]) = {
    content match {
      case Some(c) => html(c)
      case None    => HttpResponse(status = NotFound)
    }
  }
}
