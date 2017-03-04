package com.olegych.scastie
package api

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

case class Problem(
  severity: Severity,
  line: Option[Int],
  message: String
)
