package com.olegych.scastie
package api

sealed trait ScalaTargetType
object ScalaTargetType {

  def parse(targetType: String): Option[ScalaTargetType] = {
    targetType match {
      case "JVM" => Some(JVM)
      case "DOTTY" => Some(Dotty)
      case "JS" => Some(JS)
      case "NATIVE" => Some(Native)
      case "TYPELEVEL" => Some(Typelevel)
      case _ => None
    }
  }

  case object JVM extends ScalaTargetType
  case object Dotty extends ScalaTargetType
  case object JS extends ScalaTargetType
  case object Native extends ScalaTargetType
  case object Typelevel extends ScalaTargetType
}
