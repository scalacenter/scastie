package scastie.api

import io.circe.generic.semiauto._
import io.circe._

sealed trait ConsoleOutput {
  def show: String
}

case class SbtOutput(output: ProcessOutput) extends ConsoleOutput {
  def show: String = s"sbt: ${output.line}"
}

case class UserOutput(output: ProcessOutput) extends ConsoleOutput {
  def show: String = output.line
}

case class ScastieOutput(line: String) extends ConsoleOutput {
  def show: String = s"scastie: $line"
}

object ConsoleOutput {
  implicit val consoleOutputEncoder: Encoder[ConsoleOutput] = deriveEncoder[ConsoleOutput]
  implicit val consoleOutputDecoder: Decoder[ConsoleOutput] = deriveDecoder[ConsoleOutput]
}
