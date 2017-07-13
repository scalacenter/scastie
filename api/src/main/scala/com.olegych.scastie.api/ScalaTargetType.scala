package com.olegych.scastie
package api

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
