package org.scastie.client.components

import org.scastie.api._
import org.scastie.client.components.editor.SimpleEditor
import japgolly.scalajs.react._

import vdom.all._
import org.scastie.api.ScalaTarget._
import japgolly.scalajs.react.feature.ReactFragment

final case class BuildSettings(
    visible: Boolean,
    inputs: BaseInputs,
    isDarkTheme: Boolean,
    isBuildDefault: Boolean,
    isResetModalClosed: Boolean,
    setTarget: ScalaTarget ~=> Callback,
    closeResetModal: Reusable[Callback],
    resetBuild: Reusable[Callback],
    openResetModal: Reusable[Callback],
    sbtConfigChange: String ~=> Callback,
    removeScalaDependency: ScalaDependency ~=> Callback,
    updateDependencyVersion: (ScalaDependency, String) ~=> Callback,
    addScalaDependency: (ScalaDependency, Project) ~=> Callback,
    convertToScalaCli: Reusable[Callback],
    scalaCliConversionError: Option[String]
) {

  @inline def render: VdomElement = BuildSettings.component(this)
}

object BuildSettings {

  implicit val reusability: Reusability[BuildSettings] =
    Reusability.derive[BuildSettings]

  private def renderResetButton(props: BuildSettings): TagMod = {
    TagMod(
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
      )(
        "Reset"
      )
    ).when(!props.isBuildDefault && props.inputs.target.targetType != ScalaTargetType.ScalaCli)
  }

  private def scaladexSearch(props: BuildSettings, sbtInputs: SbtInputs): VdomNode = ScaladexSearch(
    removeScalaDependency = props.removeScalaDependency,
    updateDependencyVersion = props.updateDependencyVersion,
    addScalaDependency = props.addScalaDependency,
    librariesFrom = sbtInputs.librariesFrom,
    scalaTarget = sbtInputs.target,
    isDarkTheme = props.isDarkTheme
  ).render

  private def sbtExtraConfigurationPanel(props: BuildSettings, sbtInputs: SbtInputs): VdomNode =
    ReactFragment(
      h2(
        span("Extra Sbt Configuration")
      ),
      pre(cls := "configuration")(
        SimpleEditor(
          value = sbtInputs.sbtConfigExtra,
          isDarkTheme = props.isDarkTheme,
          readOnly = false,
          onChange = props.sbtConfigChange
        ).render
      ),
    )

  private def baseSbtConfiguration(props: BuildSettings, sbtInputs: SbtInputs): VdomNode =
    ReactFragment(
      h2(
        span("Base Sbt Configuration (readonly)")
      ),
      pre(cls := "configuration")(
        SimpleEditor(
          value = sbtInputs.sbtConfig,
          isDarkTheme = props.isDarkTheme,
          readOnly = true,
          onChange = Reusable.always(_ => Callback.empty)
        ).render
      )
    )

  private def baseSbtPluginsConfiguration(props: BuildSettings, sbtInputs: SbtInputs): VdomNode =
    ReactFragment(
      h2(
        span("Base Sbt Plugins Configuration (readonly)")
      ),
      pre(cls := "configuration")(
        SimpleEditor(
          value = sbtInputs.sbtPluginsConfig,
          isDarkTheme = props.isDarkTheme,
          readOnly = true,
          onChange = Reusable.always(_ => Callback.empty)
        ).render
      ),
    )

  private def sbtBuildSettingsPanel(props: BuildSettings, sbtInputs: SbtInputs): TagMod = {
    div()(
      h2(span("Scala Version")),
      VersionSelector(props.inputs.target, props.setTarget).render,
      h2(span("Libraries")),
      scaladexSearch(props, sbtInputs),
      sbtExtraConfigurationPanel(props, sbtInputs),
      baseSbtConfiguration(props, sbtInputs),
      baseSbtPluginsConfiguration(props, sbtInputs)
    )
  }

  private def scalaCliBuildSettingsPanel(props: BuildSettings, scalaCliInputs: ScalaCliInputs): TagMod = {
    div()(
      p()(
        "To use a specific version of Scala with Scala-CLI, use directives. See ",
        a(href := "https://scala-cli.virtuslab.org/docs/reference/directives/#scala-version", target := "_blank")("Scala version directive on Scala-CLI documentation"),
        "."
      ),
      p()(
        "To use libraries with Scala-CLI, use directives. See ",
        a(href := "https://scala-cli.virtuslab.org/docs/reference/directives#dependency", target := "_blank")("Dependency directive on Scala-CLI documentation"),
        "."
      ),
    )
  }

  private def render(props: BuildSettings): VdomElement = {
    val targetSpecificSettings = props.inputs match {
      case sbtInputs: SbtInputs => sbtBuildSettingsPanel(props, sbtInputs)
      case scalaCliInputs: ScalaCliInputs => scalaCliBuildSettingsPanel(props, scalaCliInputs)
      case _ => div()()
    }

    div(cls := "build-settings-container")(
      renderResetButton(props),
      h2(span("Target")),
      TargetSelector(props.inputs.target, props.setTarget).render,
      targetSpecificSettings,
    )
  }

  private val component =
    ScalaComponent
      .builder[BuildSettings]("BuildSettings")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
