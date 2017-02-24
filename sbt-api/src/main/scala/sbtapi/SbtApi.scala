package sbtapi

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

case class Problem(severity: Severity, line: Option[Int], message: String)

// TODO: stacktrace
// stack: List[StackElement]
// String  getClassName()
// String  getFileName()
// int getLineNumber()
// String  getMethodName()
// TODO: range pos ?
case class RuntimeError(message: String, line: Option[Int], fullStack: String)

sealed trait SbtLogLevel
object SbtLogLevel {
  case object Debug extends SbtLogLevel
  case object Info extends SbtLogLevel
  case object Warn extends SbtLogLevel
  case object Error extends SbtLogLevel
}

case class SbtOutput(line: String, level: Option[SbtLogLevel])
