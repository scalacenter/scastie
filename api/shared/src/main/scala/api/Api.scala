package api

import scala.concurrent.Future

trait Api {
  def run(code: String): Future[Long]
}

case class PasteProgress(id: Long, output: Seq[String], compilationInfos: List[Problem])

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

case class Problem(severity: Severity, offset: Option[Int], message: String)
