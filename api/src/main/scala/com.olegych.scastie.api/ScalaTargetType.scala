package com.olegych.scastie.api

import play.api.libs.json._

sealed trait ScalaTargetType {
  def defaultScalaTarget: ScalaTarget
}

object ScalaTargetType {

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

  implicit object ScalaTargetTypeFormat extends Format[ScalaTargetType] {
    def writes(scalaTargetType: ScalaTargetType): JsValue = {
      JsString(scalaTargetType.toString)
    }

    private val values =
      List(
        Scala2,
        Scala3,
        JS,
        Native,
        Typelevel,
        ScalaCli
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

  case object Scala2 extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.Jvm.default
  }

  case object Scala3 extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.Scala3.default
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

  case object ScalaCli extends ScalaTargetType {
    def defaultScalaTarget: ScalaTarget = ScalaTarget.ScalaCli.default
  }
}
