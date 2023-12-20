package org.scastie.runtime.api

case class ScalaJsResult(instrumentations: List[Instrumentation], error: Option[RuntimeError]) {
  def asJsonString: String = error match {
    case Some(value) => s"""{"error":${value.asJsonString}}"""
    case None =>
      val instrumentationJson = instrumentations.map(_.asJsonString).mkString("[", ",", "]")
      s"""{"instrumentations":$instrumentationJson}"""
  }
}
