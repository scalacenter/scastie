package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.editor.SimpleEditor
import japgolly.scalajs.react._

import vdom.all._
import com.olegych.scastie.api.ScalaTarget._
import japgolly.scalajs.react.feature.ReactFragment

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

    convertToScalaCli: Reusable[Callback],
    scalaCliConversionError: Option[String]
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

    val isScalaCli = props.scalaTarget match {
      case _: ScalaCli => true
      case _ => false
    }

    div(cls := "build-settings-container")(
      resetButton,
      h2(
        span("Target"),
      ),
      TargetSelector(props.scalaTarget, props.setTarget).render,
      h2(
        span("Scala Version")
      ),
      VersionSelector(props.scalaTarget, props.setTarget).render.unless(isScalaCli),
      p()(
        "To use a specific version of Scala with Scala-CLI, use directives. See ",
        a(href := "https://scala-cli.virtuslab.org/docs/reference/directives/#scala-version", target := "_blank")("Scala version directive on Scala-CLI documentation"),
        "."
      ).when(isScalaCli),
      h2(
        span("Libraries")
      ),
      scaladexSearch.unless(isScalaCli),
      p()(
        "To use libraries with Scala-CLI, use directives. See ",
        a(href := "https://scala-cli.virtuslab.org/docs/reference/directives#dependency", target := "_blank")("Dependency directive on Scala-CLI documentation"),
        "."
      ).when(isScalaCli),
      ReactFragment(
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
        ),

        h2(
          span("Convert to Scala-CLI")
        ),
        div(
          title := "Convert to Scala-CLI",
          onClick --> props.convertToScalaCli,
          role := "button",
          cls := "btn"
        )("Convert to Scala-CLI"),

        props.scalaCliConversionError.map(err => p()(s"Failed to convert to Scala-CLI: $err"))
      ).when(!isScalaCli)
    )
  }

  private val component =
    ScalaComponent
      .builder[BuildSettings]("BuildSettings")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
