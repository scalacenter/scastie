package api

import scala.concurrent.Future

trait Api {
  def run(code: String): Future[Long]
}