package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

import org.scastie.api

sealed trait ScalaTargetType {
  def defaultScalaTarget: ScalaTarget = {
    this match {
      case ScalaTargetType.Scala2 => api.Scala2.default
      case ScalaTargetType.JS => api.Js.default
      case ScalaTargetType.Native => api.Native.default
      case ScalaTargetType.Typelevel => api.Typelevel.default
      case ScalaTargetType.Scala3 => api.Scala3.default
      case ScalaTargetType.ScalaCli => api.ScalaCli.default
    }
  }

}

object ScalaTargetType {
  implicit val scalaTargetTypeEncoder: Encoder[ScalaTargetType] = deriveEncoder[ScalaTargetType]
  implicit val scalaTargetTypeDecoder: Decoder[ScalaTargetType] = deriveDecoder[ScalaTargetType]

  def parse(targetType: String): Option[ScalaTargetType] = {
    targetType match {
      case "JVM"       => Some(Scala2)
      case "DOTTY"     => Some(Scala3)
      case "JS"        => Some(JS)
      case "NATIVE"    => Some(Native)
      case "TYPELEVEL" => Some(Typelevel)
      case "SCALACLI"  => Some(ScalaCli)
      case _           => None
    }
  }


  case object Scala2 extends ScalaTargetType
  case object Scala3 extends ScalaTargetType
  case object JS extends ScalaTargetType
  case object Native extends ScalaTargetType
  case object Typelevel extends ScalaTargetType
  case object ScalaCli extends ScalaTargetType
}
