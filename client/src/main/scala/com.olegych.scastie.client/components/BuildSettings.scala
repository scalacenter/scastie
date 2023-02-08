package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.editor.SimpleEditor
import japgolly.scalajs.react._

import vdom.all._

final case class BuildSettings(
    visible: Boolean,
    librariesFrom: Map[ScalaDependency, Project],
    isDarkTheme: Boolean,
    isBuildDefault: Boolean,
    isResetModalClosed: Boolean,
    scalaTarget: ScalaTarget,
    sbtConfigExtra: String,
    sbtConfig: String,
    sbtPluginsConfig: String,
    setTarget: ScalaTarget ~=> Callback,
    closeResetModal: Reusable[Callback],
    resetBuild: Reusable[Callback],
    openResetModal: Reusable[Callback],
    sbtConfigChange: String ~=> Callback,
    removeScalaDependency: ScalaDependency ~=> Callback,
    updateDependencyVersion: (ScalaDependency, String) ~=> Callback,
    addScalaDependency: (ScalaDependency, Project) ~=> Callback
) {

  @inline def render: VdomElement = BuildSettings.component(this)
}

object BuildSettings {

  implicit val reusability: Reusability[BuildSettings] =
    Reusability.derive[BuildSettings]

  private def render(props: BuildSettings): VdomElement = {

    val resetButton = TagMod(
      PromptModal(
        isDarkTheme = props.isDarkTheme,
        modalText = "Reset Build",
        modalId = "reset-build-modal",
        isClosed = props.isResetModalClosed,
        close = props.closeResetModal,
        actionText = "Are you sure you want to reset the build ?",
        actionLabel = "Reset",
        action = props.resetBuild
      ).render,
      div(
        title := "Reset your configuration",
        onClick --> props.openResetModal,
        role := "button",
        cls := "btn",
        if (props.isBuildDefault) visibility.collapse else visibility.visible
      )(
        "Reset"
      )
    )

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
        span("Target"),
      ),
      TargetSelector(props.scalaTarget, props.setTarget).render,
      h2(
        span("Scala Version")
      ),
      VersionSelector(props.scalaTarget, props.setTarget).render,
      h2(
        span("Libraries")
      ),
      scaladexSearch,
      h2(
        span("Extra Sbt Configuration")
      ),
      pre(cls := "configuration")(
        SimpleEditor(
          value = props.sbtConfigExtra,
          isDarkTheme = props.isDarkTheme,
          readOnly = false,
          onChange = props.sbtConfigChange
        ).render
      ),
      h2(
        span("Base Sbt Configuration (readonly)")
      ),
      pre(cls := "configuration")(
        SimpleEditor(
          value = props.sbtConfig,
          isDarkTheme = props.isDarkTheme,
          readOnly = true,
          onChange = Reusable.always(_ => Callback.empty)
        ).render
      ),
      h2(
        span("Base Sbt Plugins Configuration (readonly)")
      ),
      pre(cls := "configuration")(
        SimpleEditor(
          value = props.sbtPluginsConfig,
          isDarkTheme = props.isDarkTheme,
          readOnly = true,
          onChange = Reusable.always(_ => Callback.empty)
        ).render
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[BuildSettings]("BuildSettings")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
