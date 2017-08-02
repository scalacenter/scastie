package com.olegych.scastie.api

import play.api.libs.json._

sealed trait ScalaTargetType {
  def defaultScalaTarget: ScalaTarget
}

object ScalaTargetType {

  def parse(targetType: String): Option[ScalaTargetType] = {
    targetType match {
      case "JVM"       => Some(JVM)
      case "DOTTY"     => Some(Dotty)
      case "JS"        => Some(JS)
      case "NATIVE"    => Some(Native)
      case "TYPELEVEL" => Some(Typelevel)
      case _           => None
    }
  }

  implicit object ScalaTargetTypeFormat extends Format[ScalaTargetType] {
    def writes(scalaTargetType: ScalaTargetType): JsValue = {
      JsString(scalaTargetType.toString)
    }

    private val values =
      List(
        JVM,
        Dotty,
        JS,
        Native,
        Typelevel
      ).map(v => (v.toString, v)).toMap

    def reads(json: JsValue): JsResult[ScalaTargetType] = {
      json match {
        case JsString(tpe) => {
          values.get(tpe) match {
            case Some(v) => JsSuccess(v)
            case _       => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }

  case object JVM extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.Jvm.default
  }

  case object Dotty extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.Dotty.default
  }

  case object JS extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.Js.default
  }

  case object Native extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.Native.default
  }

  case object Typelevel extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.Typelevel.default
  }
}
