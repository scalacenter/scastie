package org.scastie.client.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.feature.ReactFragment
import org.scastie.api._
import org.scastie.api.ScalaTarget._
import org.scastie.client.components.editor.SimpleEditor
import org.scastie.client.i18n.I18n
import vdom.all._

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
  scalaCliConversionError: Option[String],
  language: String
) {

  @inline def render: VdomElement = BuildSettings.component(this)
}

object BuildSettings {
  private def renderWithElement(template: String, elementBuilder: String => VdomElement): VdomElement = {
    val elementRegex = """\{([^}]+)\}""".r
    elementRegex.findFirstMatchIn(template) match {
      case Some(m) =>
        val before = template.substring(0, m.start)
        val elementContent = m.group(1)
        val element = elementBuilder(elementContent)
        val after = template.substring(m.end)
        span(before, element, after)
      case None =>
        span(template)
    }
  }
  var mutableList: List[(ScalaDependency, Project)] = List.empty[(ScalaDependency, Project)]

  implicit val reusability: Reusability[BuildSettings] = Reusability.derive[BuildSettings]

  private def renderResetButton(props: BuildSettings): VdomNode = {
    ReactFragment(
      PromptModal(
        isDarkTheme = props.isDarkTheme,
        modalText = I18n.t("build.reset_title"),
        modalId = "reset-build-modal",
        isClosed = props.isResetModalClosed,
        close = props.closeResetModal,
        actionText = I18n.t("build.reset_confirmation"),
        actionLabel = I18n.t("build.reset"),
        action = props.resetBuild
      ).render,
      div(
        hidden := props.isBuildDefault || props.inputs.target.targetType == ScalaTargetType.ScalaCli,
        title  := I18n.t("build.reset_tooltip"),
        onClick --> props.openResetModal,
        role := "button",
        cls  := "btn"
      )(
        I18n.t("build.reset")
      )
    )
  }

  val addScalaDependency: List[(ScalaDependency, Project)] ~=> Callback = Reusable.byRef { dependencies =>
    Callback {
      mutableList = dependencies
    }
  }

  private def scaladexSearch(props: BuildSettings, sbtInputs: SbtInputs): VdomNode = {
    ScaladexSearch(
      removeScalaDependency = props.removeScalaDependency,
      updateDependencyVersion = props.updateDependencyVersion,
      addScalaDependency = props.addScalaDependency,
      libraries = sbtInputs.libraries,
      scalaTarget = sbtInputs.target,
      isDarkTheme = props.isDarkTheme,
      language = props.language
    ).render
  }

  private def sbtExtraConfigurationPanel(props: BuildSettings, sbtInputs: SbtInputs): VdomNode = ReactFragment(
    h2(
      span(I18n.t("build.sbt_config"))
    ),
    pre(cls := "configuration")(
      SimpleEditor(
        value = sbtInputs.sbtConfigExtra,
        isDarkTheme = props.isDarkTheme,
        readOnly = false,
        onChange = props.sbtConfigChange
      ).render
    )
  )

  private def baseSbtConfiguration(props: BuildSettings, sbtInputs: SbtInputs): VdomNode = ReactFragment(
    h2(
      span(I18n.t("build.sbt_base_config"))
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

  private def baseSbtPluginsConfiguration(props: BuildSettings, sbtInputs: SbtInputs): VdomNode = ReactFragment(
    h2(
      span(I18n.t("build.sbt_plugins_config"))
    ),
    pre(cls := "configuration")(
      SimpleEditor(
        value = sbtInputs.sbtPluginsConfig,
        isDarkTheme = props.isDarkTheme,
        readOnly = true,
        onChange = Reusable.always(_ => Callback.empty)
      ).render
    )
  )

  private def sbtBuildSettingsPanel(props: BuildSettings, sbtInputs: SbtInputs): TagMod = {
    div()(
      h2(span(I18n.t("build.scala_version"))),
      VersionSelector(sbtInputs.target, props.setTarget).render,
      h2(span(I18n.t("build.libraries"))),
      scaladexSearch(props, sbtInputs),
      sbtExtraConfigurationPanel(props, sbtInputs),
      baseSbtConfiguration(props, sbtInputs),
      baseSbtPluginsConfiguration(props, sbtInputs)
    )
  }

  private def scalaCliBuildSettingsPanel(props: BuildSettings, scalaCliInputs: ScalaCliInputs): TagMod = {
    div()(
      p(
        renderWithElement(
          I18n.t("build.scala_cli_version_doc"),
          content => a(href := "https://scala-cli.virtuslab.org/docs/reference/directives/#scala-version", target := "_blank")(content)
        )
      ),
      p(
        renderWithElement(
          I18n.t("build.scala_cli_dependency_doc"),
          content => a(href := "https://scala-cli.virtuslab.org/docs/reference/directives#dependency", target := "_blank")(content)
        )
      )
    )
  }

  private def render(props: BuildSettings): VdomElement = {
    val targetSpecificSettings = props.inputs match {
      case sbtInputs: SbtInputs           => sbtBuildSettingsPanel(props, sbtInputs)
      case scalaCliInputs: ScalaCliInputs => scalaCliBuildSettingsPanel(props, scalaCliInputs)
    }

    div(cls := "build-settings-container")(
      renderResetButton(props),
      h2(span(I18n.t("build.target"))),
      TargetSelector(props.inputs.target, props.setTarget).render,
      targetSpecificSettings
    )
  }

  private val component = ScalaComponent
    .builder[BuildSettings]("BuildSettings")
    .render_P(render)
    .configure(Reusability.shouldComponentUpdate)
    .build

}
