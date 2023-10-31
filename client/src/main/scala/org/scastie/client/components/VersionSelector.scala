package org.scastie.client.components

import org.scastie.api._
import japgolly.scalajs.react._

import vdom.all._

case class VersionSelector(scalaTarget: SbtScalaTarget, onChange: ScalaTarget ~=> Callback) {
  @inline def render: VdomElement = VersionSelector.versionSelectorHook(this)
}

object VersionSelector {

  val versionSelectorHook =
    ScalaFnComponent
      .withHooks[VersionSelector]
      .render(props => {
        def versionSelectors(scalaVersion: String) =
          props.scalaTarget match {
            case d: Jvm       => Jvm.apply(scalaVersion)
            case d: Typelevel => Typelevel.apply(scalaVersion)
            case d: Scala3    => Scala3.apply(scalaVersion)
            case js: Js       => Js(scalaVersion, js.scalaJsVersion)
            case n: Native    => Native(n.scalaNativeVersion, n.scalaVersion)
          }

        ul(cls := "suggestedVersions")(
          ScalaVersions
            .suggestedScalaVersions(props.scalaTarget.targetType)
            .map { suggestedVersion =>
              li(
                input(
                  `type` := "radio",
                  id := s"scala-$suggestedVersion",
                  value := suggestedVersion,
                  name := "scalaV",
                  onChange --> props.onChange(versionSelectors(suggestedVersion)),
                  checked := props.scalaTarget.scalaVersion == suggestedVersion
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
                    props.onChange(versionSelectors(e.target.value))
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
