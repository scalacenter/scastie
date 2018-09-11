package com.olegych.scastie.api

import play.api.libs.json._

object ReleaseOptions {
  implicit val formatReleaseOptions: OFormat[ReleaseOptions] =
    Json.format[ReleaseOptions]
}

case class ReleaseOptions(groupId: String, versions: List[String], version: String)

// case class MavenReference(groupId: String, artifactId: String, version: String)

object Outputs {
  implicit val formatOutputs: OFormat[Outputs] =
    Json.format[Outputs]

  def default: Outputs = Outputs(
    consoleOutputs = Vector(),
    compilationInfos = Set(),
    instrumentations = Set(),
    runtimeError = None,
    sbtError = false
  )
}
case class Outputs(
    consoleOutputs: Vector[ConsoleOutput],
    compilationInfos: Set[Problem],
    instrumentations: Set[Instrumentation],
    runtimeError: Option[RuntimeError],
    sbtError: Boolean
) {

  def console: String = consoleOutputs.map(_.show).mkString("\n")

  def isClearable: Boolean =
    consoleOutputs.nonEmpty ||
      compilationInfos.nonEmpty ||
      instrumentations.nonEmpty ||
      runtimeError.isDefined
}

object Position {
  implicit val formatPosition: OFormat[Position] =
    Json.format[Position]
}

case class Position(start: Int, end: Int)
