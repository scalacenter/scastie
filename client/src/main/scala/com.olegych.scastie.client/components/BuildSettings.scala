package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.editor.SimpleEditor
import japgolly.scalajs.react._

import vdom.all._

import com.olegych.scastie.client.i18n.I18n

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
    addScalaDependency: (ScalaDependency, Project) ~=> Callback,
    language: String
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
        modalText = I18n.t("Reset Build"),
        modalId = "reset-build-modal",
        isClosed = props.isResetModalClosed,
        close = props.closeResetModal,
        actionText = I18n.t("Are you sure you want to reset the build ?"),
        actionLabel = I18n.t("Reset"),
        action = props.resetBuild
      ).render,
      div(
        title := I18n.t("Reset your configuration"),
        onClick --> props.openResetModal,
        role := "button",
        cls := "btn",
        if (props.isBuildDefault) visibility.collapse else visibility.visible
      )(
        I18n.t("Reset")
      )
    )

    val scaladexSearch = ScaladexSearch(
      removeScalaDependency = props.removeScalaDependency,
      updateDependencyVersion = props.updateDependencyVersion,
      addScalaDependency = props.addScalaDependency,
      librariesFrom = props.librariesFrom,
      scalaTarget = props.scalaTarget,
      isDarkTheme = props.isDarkTheme,
      language = props.language
    ).render

    div(cls := "build-settings-container")(
      resetButton,
      h2(
        span(I18n.t("Target")),
      ),
      TargetSelector(props.scalaTarget, props.setTarget).render,
      h2(
        span(I18n.t("Scala Version"))
      ),
      VersionSelector(props.scalaTarget, props.setTarget).render,
      h2(
        span(I18n.t("Libraries"))
      ),
      scaladexSearch,
      h2(
        span(I18n.t("Extra Sbt Configuration"))
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
        span(I18n.t("Base Sbt Configuration (readonly)"))
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
        span(I18n.t("Base Sbt Plugins Configuration (readonly)"))
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
