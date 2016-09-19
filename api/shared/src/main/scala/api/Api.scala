package api

import scala.concurrent.Future

trait Api {
  def run(code: String): Future[Long]
}

case class PasteProgress(id: Long, output: Seq[String])