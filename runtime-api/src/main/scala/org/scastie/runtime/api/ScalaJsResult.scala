package org.scastie.runtime.api

case class ScalaJsResult(instrumentations: List[Instrumentation], error: Option[RuntimeError]) {
  def asJsonString: String = {
    val instrumentationJson = instrumentations.map(_.asJsonString).mkString("[", ",", "]")
    val errorJson = error.map(_.asJsonString).getOrElse("null")
    s"""{"instrumentations":$instrumentationJson,"error":"$errorJson"}"""
  }
}
