package org.scastie.client.components

import org.scastie.api._
import japgolly.scalajs.react._

import vdom.all._
import org.scastie.api.ScalaTargetType.Scala2
import org.scastie.api.ScalaTargetType.JS

case class TargetSelector(scalaTarget: ScalaTarget, onChange: ScalaTarget ~=> Callback) {
  @inline def render: VdomElement = TargetSelector.targetSelector(this)
}

object TargetSelector {

  val targetTypes = List[ScalaTargetType](
    ScalaTargetType.ScalaCli,
    ScalaTargetType.Scala3,
    ScalaTargetType.Scala2,
    ScalaTargetType.JS
    // ScalaTargetType.Native
  )

  def labelFor(targetType: ScalaTargetType) = {
    targetType match {
      case ScalaTargetType.ScalaCli  => "Scala-CLI"
      case ScalaTargetType.Scala2    => "Scala 2"
      case ScalaTargetType.JS        => "Scala.js"
      case ScalaTargetType.Scala3    => "Scala 3"
      case ScalaTargetType.Native    => "Native"
      case ScalaTargetType.Typelevel => "Typelevel"
    }
  }


  val targetSelector =
    ScalaFnComponent
      .withHooks[TargetSelector]
      .render(props => {
        div(
          ul(cls := "target")(
            targetTypes.map { targetType =>
              val targetLabel = labelFor(targetType)
              li(
                input(
                  `type` := "radio",
                  id := targetLabel,
                  value := targetLabel,
                  name := "target",
                  onChange --> props.onChange(targetType.defaultScalaTarget),
                  checked := targetType == props.scalaTarget.targetType
                ),
                label(`for` := targetLabel, role := "button", cls := "radio", targetLabel)
              )
            }.toTagMod
          )
        )
      })
}
