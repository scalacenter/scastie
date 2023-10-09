package scastie.runtime.api

case class ScalaJsResult(instrumentations: List[Instrumentation], error: Option[RuntimeError])
