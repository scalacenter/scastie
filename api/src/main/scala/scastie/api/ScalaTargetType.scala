package scastie.api

import io.circe.generic.semiauto._
import io.circe._

sealed trait ScalaTargetType {
  def defaultScalaTarget: ScalaTarget = {
    this match {
      case ScalaTargetType.Scala2 => scastie.api.Jvm.default
      case ScalaTargetType.JS => scastie.api.Js.default
      case ScalaTargetType.Native => scastie.api.Native.default
      case ScalaTargetType.Typelevel => scastie.api.Typelevel.default
      case ScalaTargetType.Scala3 => scastie.api.Scala3.default
      case ScalaTargetType.ScalaCli => scastie.api.ScalaCli.default
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
