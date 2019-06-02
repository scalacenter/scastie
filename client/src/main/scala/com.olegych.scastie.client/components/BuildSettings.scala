package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import japgolly.scalajs.react._, vdom.TagOf, vdom.all._, extra._
import org.scalajs.dom.html.Div

final case class BuildSettings(
    visible: Boolean,
    librariesFrom: Map[ScalaDependency, Project],
    isDarkTheme: Boolean,
    isBuildDefault: Boolean,
    isResetModalClosed: Boolean,
    scalaTarget: ScalaTarget,
    sbtConfigExtra: String,
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
    Reusability.caseClass[BuildSettings]

  def renderTarget(props: BuildSettings): TagOf[Div] = {

    val targetTypes = List[ScalaTargetType](
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
            label(`for` := targetLabel, role := "button", cls := "radio", targetLabel)
          )
        }.toTagMod
      )
    )
  }

  def renderVersions(props: BuildSettings): TagMod = {
    val suggestedVersions = List(
      "2.13.0-RC3",
      "2.12.8",
      "2.11.12"
    )

    def setScalaVersion(
        targetFun: String => ScalaTarget
    )(event: ReactEventFromInput): Callback =
      props.setTarget(targetFun(event.target.value))

    def versionSelector(scalaVersion: String, targetFun: String => ScalaTarget) = {
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
              label(`for` := s"scala-$suggestedVersion", cls := "radio", role := "button", suggestedVersion)
            )
          }.toTagMod,
          li(
            input(`type` := "radio", id := scalaVersion, value := scalaVersion, name := "scalaV", handler(scalaVersion)),
            label(
              div(cls := "select-wrapper")(
                select(name := "scalaVersion", value := scalaVersion.toString, onChange ==> setScalaVersion(targetFun))(
                  ScalaVersions.allVersions
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
        case ScalaTarget.Jvm(scalaVersion) =>
          versionSelector(scalaVersion, ScalaTarget.Jvm.apply)

        case ScalaTarget.Typelevel(scalaVersion) =>
          versionSelector(scalaVersion, ScalaTarget.Typelevel.apply)

        case d: ScalaTarget.Dotty =>
          div(d.dottyVersion)

        case js: ScalaTarget.Js =>
          div(s"${js.scalaJsVersion} on Scala ${js.scalaVersion}")

        case n: ScalaTarget.Native =>
          div(s"${n.scalaNativeVersion} on Scala ${n.scalaVersion}")
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
          modalId = "reset-build-modal",
          isClosed = props.isResetModalClosed,
          close = props.closeResetModal,
          actionText = "Are you sure you want to reset the build ?",
          actionLabel = "Reset",
          action = props.resetBuild
        ).render,
        div(title := "Reset your configuration", onClick --> props.openResetModal, role := "button", cls := "btn")(
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
          value = props.sbtConfigExtra,
          theme = s"solarized $theme",
          readOnly = false,
          onChange = props.sbtConfigChange
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
