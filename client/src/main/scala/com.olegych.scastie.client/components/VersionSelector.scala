package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import japgolly.scalajs.react._

import vdom.all._
import com.olegych.scastie.buildinfo.BuildInfo

case class VersionSelector(scalaTarget: ScalaTarget, onChange: ScalaTarget ~=> Callback) {
  @inline def render: VdomElement = VersionSelector.versionSelectorHook(this)
}

object VersionSelector {

  val versionSelectorHook =
    ScalaFnComponent
      .withHooks[VersionSelector]
      .render(props => {
        def versionSelectors(scalaVersion: String) =
          props.scalaTarget match {
            case d: ScalaTarget.Jvm       => ScalaTarget.Jvm.apply(scalaVersion)
            case d: ScalaTarget.Typelevel => ScalaTarget.Typelevel.apply(scalaVersion)
            case d: ScalaTarget.Scala3    => ScalaTarget.Scala3.apply(scalaVersion)
            case js: ScalaTarget.Js       => ScalaTarget.Js(scalaVersion, js.scalaJsVersion)
            case n: ScalaTarget.Native    => ScalaTarget.Native(n.scalaNativeVersion, n.scalaVersion)
          }

        def renderRecommended3Versions(scalaVersion: String) = {
          if (scalaVersion == BuildInfo.stableLTS) s"$scalaVersion LTS"
          else if (scalaVersion == BuildInfo.stableNext) s"$scalaVersion Next"
          else scalaVersion
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
                label(`for` := s"scala-$suggestedVersion", className := "radio", role := "button", renderRecommended3Versions(suggestedVersion))
              )
            }
            .toTagMod,
          li(
            label(
              div(cls := "select-wrapper"){
                val isRecommended = ScalaVersions
                    .suggestedScalaVersions(props.scalaTarget.targetType)
                    .contains(props.scalaTarget.scalaVersion)

                select(
                  name := "scalaVersion",
                  onChange ==> { (e: ReactEventFromInput) =>
                    props.onChange(versionSelectors(e.target.value))
                  },
                  value := {if (isRecommended) "Other" else props.scalaTarget.scalaVersion},
                  TagMod.when(!isRecommended)(className := "selected-option")
                )(
                  ScalaVersions
                    .allVersions(props.scalaTarget.targetType)
                    .map(version => option(version))
                    .prepended(option("Other")(hidden := true, disabled := true))
                    .toTagMod
                )
              }
            )
          )
        )
      })
}
