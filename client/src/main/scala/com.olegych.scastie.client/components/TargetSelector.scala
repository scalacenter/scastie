package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.editor.SimpleEditor
import japgolly.scalajs.react._
import org.scalajs.dom.html.Div

import vdom.TagOf
import vdom.all._
import extra._

case class TargetSelector(scalaTarget: ScalaTarget, onChange: ScalaTarget ~=> Callback) {
  @inline def render: VdomElement = TargetSelector.targetSelector(this)
}

object TargetSelector {

  val targetTypes = List[ScalaTargetType](
    ScalaTargetType.Scala3,
    ScalaTargetType.Scala2,
    ScalaTargetType.JS
    // ScalaTargetType.Native
  )

  def labelFor(targetType: ScalaTargetType) = {
    targetType match {
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
      .useStateBy[ScalaTarget](_.scalaTarget)
      .useEffectBy((props, newTarget) => props.onChange(newTarget.value))
      .render((props, currentTarget) => {
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
                  onChange --> currentTarget.setState(targetType.defaultScalaTarget),
                  checked := targetType == currentTarget.value.targetType
                ),
                label(`for` := targetLabel, role := "button", cls := "radio", targetLabel)
              )
            }.toTagMod
          )
        )
      })
}
