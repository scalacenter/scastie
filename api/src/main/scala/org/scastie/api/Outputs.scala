package org.scastie.api

import org.scastie.runtime.api._

import io.circe._
import io.circe.generic.semiauto._
import RuntimeCodecs._

object ReleaseOptions {
  implicit val releaseOptionsEncoder: Encoder[ReleaseOptions] = deriveEncoder[ReleaseOptions]
  implicit val releaseOptionsDecoder: Decoder[ReleaseOptions] = deriveDecoder[ReleaseOptions]
}

case class ReleaseOptions(groupId: String, versions: List[String], version: String)

// case class MavenReference(groupId: String, artifactId: String, version: String)

object Outputs {
  implicit val outputsEncoder: Encoder[Outputs] = deriveEncoder[Outputs]
  implicit val outputsDecoder: Decoder[Outputs] = deriveDecoder[Outputs]

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

  def console: String = consoleOutputs.mkString("\n")

  def isClearable: Boolean = consoleOutputs.nonEmpty ||
    compilationInfos.nonEmpty ||
    instrumentations.nonEmpty ||
    runtimeError.isDefined

}
