package org.scastie.client.components

import org.scastie.api._
import org.scastie.client.i18n.I18n
import japgolly.scalajs.react._

import vdom.all._
import org.scastie.buildinfo.BuildInfo

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
            case d: Scala2       => Scala2.apply(scalaVersion)
            case d: Typelevel => Typelevel.apply(scalaVersion)
            case d: Scala3    => Scala3.apply(scalaVersion)
            case js: Js       => Js(scalaVersion, js.scalaJsVersion)
            case n: Native    => Native(n.scalaNativeVersion, n.scalaVersion)
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
                  value := {if (isRecommended) I18n.t("build.other") else props.scalaTarget.scalaVersion},
                  TagMod.when(!isRecommended)(className := "selected-option")
                )(
                  ScalaVersions
                    .allVersions(props.scalaTarget.targetType)
                    .map(version => option(version))
                    .prepended(option(I18n.t("build.other"))(hidden := true, disabled := true))
                    .toTagMod
                )
              }
            )
          )
        )
      })
}
