package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.editor.SimpleEditor
import japgolly.scalajs.react._
import org.scalajs.dom.html.Div

import vdom.TagOf
import vdom.all._
import extra._

case class VersionSelector(scalaTarget: ScalaTarget, onChange: ScalaTarget ~=> Callback) {
  @inline def render: VdomElement = VersionSelector.versionSelectorHook(this)
}

object VersionSelector {

  val versionSelectorHook =
    ScalaFnComponent
      .withHooks[VersionSelector]
      .useStateBy[ScalaTarget](_.scalaTarget)
      .useEffectBy((props, newVersion) => {
        if (newVersion.value.targetType != props.scalaTarget.targetType) {
          newVersion.setState(props.scalaTarget)
        } else
          props.onChange(newVersion.value)
      })
      .render((props, currentVersion) => {
        def versionSelectors(scalaVersion: String) =
          props.scalaTarget match {
            case d: ScalaTarget.Jvm       => ScalaTarget.Jvm.apply(scalaVersion)
            case d: ScalaTarget.Typelevel => ScalaTarget.Typelevel.apply(scalaVersion)
            case d: ScalaTarget.Scala3    => ScalaTarget.Scala3.apply(scalaVersion)
            case js: ScalaTarget.Js       => ScalaTarget.Js(scalaVersion, js.scalaJsVersion)
            case n: ScalaTarget.Native    => ScalaTarget.Native(n.scalaNativeVersion, n.scalaVersion)
          }

        ul(cls := "suggestedVersions")(
          ScalaVersions
            .suggestedScalaVersions(currentVersion.value.targetType)
            .map { suggestedVersion =>
              li(
                input(
                  `type` := "radio",
                  id := s"scala-$suggestedVersion",
                  value := suggestedVersion,
                  name := "scalaV",
                  onChange --> currentVersion.setState(versionSelectors(suggestedVersion)),
                  checked := currentVersion.value.scalaVersion == suggestedVersion
                ),
                label(`for` := s"scala-$suggestedVersion", cls := "radio", role := "button", suggestedVersion)
              )
            }
            .toTagMod,
          li(
            label(
              div(cls := "select-wrapper")(
                select(
                  name := "scalaVersion",
                  onChange ==> { (e: ReactEventFromInput) =>
                    currentVersion.setState(versionSelectors(e.target.value))
                  },
                )(
                  ScalaVersions
                    .allVersions(props.scalaTarget.targetType)
                    .map(version => option(version))
                    .toTagMod
                )
              )
            )
          )
        )
      })
}
