package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.proto._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.TagOf
import japgolly.scalajs.react.vdom.all._

import org.scalajs.dom.html.Div

final case class BuildSettings(
    setTarget: ScalaTarget => Callback,
    closeResetModal: Callback,
    resetBuild: Callback,
    openResetModal: Callback,
    sbtConfigChange: String => Callback,
    removeScalaDependency: ScalaDependency => Callback,
    updateDependencyVersion: (ScalaDependency, String) => Callback,
    addScalaDependency: (ScalaDependency, Project) => Callback,
    librariesFrom: Map[ScalaDependency, Project],
    isDarkTheme: Boolean,
    isBuildDefault: Boolean,
    isResetModalClosed: Boolean,
    scalaTarget: ScalaTarget,
    sbtConfigExtra: String
) {

  @inline def render: VdomElement = BuildSettings.component(this)
}

object BuildSettings {

  def renderTarget(props: BuildSettings): TagOf[Div] = {

    val targetTypes: List[ScalaTargetType] = List(
      ScalaTargetType.JVM,
      ScalaTargetType.Dotty,
      ScalaTargetType.Typelevel,
      ScalaTargetType.JS //,
      // ScalaTargetType.Native
    )

    def labelFor(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM       => "Scalac"
        case ScalaTargetType.JS        => "Scala.js"
        case ScalaTargetType.Dotty     => "Dotty"
        case ScalaTargetType.Native    => "Native"
        case ScalaTargetType.Typelevel => "Typelevel"
      }
    }

    def selected(targetType: ScalaTargetType) =
      TagMod(`checked` := targetType == props.scalaTarget.targetType)

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
              onChange --> props.setTarget(targetType.defaultScalaTarget),
              selected(targetType)
            ),
            label(`for` := targetLabel,
                  role := "button",
                  cls := "radio",
                  targetLabel)
          )
        }.toTagMod
      )
    )
  }

  def renderVersions(props: BuildSettings): TagMod = {
    val suggestedVersions = List(
      // "2.13.0-M1",
      "2.12.3",
      "2.11.11"
    )

    def setScalaVersion(
        targetFun: String => ScalaTarget
    )(event: ReactEventFromInput): Callback =
      props.setTarget(targetFun(event.target.value))

    val notSupported = div("Not supported")

    def versionSelector(scalaVersion: String,
                        targetFun: String => ScalaTarget) = {
      def handler(scalaVersion: String) =
        TagMod(onChange --> props.setTarget(targetFun(scalaVersion)))

      def selected(version: String) =
        TagMod(`checked` := targetFun(version) == props.scalaTarget)

      TagMod(
        ul(cls := "suggestedVersions")(
          suggestedVersions.map { suggestedVersion =>
            li(
              input(`type` := "radio",
                    id := s"scala-$suggestedVersion",
                    value := suggestedVersion,
                    name := "scalaV",
                    handler(suggestedVersion),
                    selected(suggestedVersion)),
              label(`for` := s"scala-$suggestedVersion",
                    cls := "radio",
                    role := "button",
                    suggestedVersion)
            )
          }.toTagMod,
          li(
            input(`type` := "radio",
                  id := scalaVersion,
                  value := scalaVersion,
                  name := "scalaV",
                  handler(scalaVersion)),
            label(
              div(cls := "select-wrapper")(
                select(name := "scalaVersion",
                       value := scalaVersion.toString,
                       onChange ==> setScalaVersion(targetFun))(
                  ScalaTarget.allVersions
                    .map(version => option(version))
                    .toTagMod
                )
              )
            )
          )
        )
      )
    }

    val versionSelectors =
      props.scalaTarget match {
        case ScalaTarget.PlainScala(scalaVersion) =>
          versionSelector(scalaVersion, ScalaTarget.PlainScala.apply)

        case ScalaTarget.Typelevel(scalaVersion) =>
          versionSelector(scalaVersion, ScalaTarget.Typelevel.apply)

        case ScalaTarget.Dotty =>
          notSupported

        case ScalaTarget.ScalaJs(_, _) =>
          notSupported

        case ScalaTarget.Native(_, _) =>
          notSupported
      }

    versionSelectors
  }

  private def render(props: BuildSettings): VdomElement = {
    val theme =
      if (props.isDarkTheme) "dark"
      else "light"

    val resetButton =
      TagMod(
        PromptModal(
          modalText = "Reset Build",
          isClosed = props.isResetModalClosed,
          close = props.closeResetModal,
          actionText = "Are you sure you want to reset the build ?",
          actionLabel = "Reset",
          action = props.resetBuild
        ).render,
        div(title := "Reset your configuration",
            onClick --> props.openResetModal,
            role := "button",
            cls := "btn")(
          "Reset"
        )
      ).when(!props.isBuildDefault)

    val scaladexSearch = ScaladexSearch(
      removeScalaDependency = props.removeScalaDependency,
      updateDependencyVersion = props.updateDependencyVersion,
      addScalaDependency = props.addScalaDependency,
      librariesFrom = props.librariesFrom,
      scalaTarget = props.scalaTarget
    ).render

    div(cls := "build-settings-container")(
      resetButton,
      h2(
        span("Target")
      ),
      renderTarget(props),
      h2(
        span("Scala Version")
      ),
      renderVersions(props),
      h2(
        span("Libraries")
      ),
      scaladexSearch,
      h2(
        span("Sbt Configuration")
      ),
      pre(cls := "configuration")(
        CodeMirrorEditor(
          CodeMirrorEditor.Settings(
            value = props.sbtConfigExtra,
            theme = s"solarized $theme",
            readOnly = false
          ),
          CodeMirrorEditor.Handler(
            updatedSettings => props.sbtConfigChange(updatedSettings)
          )
        )
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[BuildSettings]("BuildSettings")
      .render_P(render)
      .build
}
