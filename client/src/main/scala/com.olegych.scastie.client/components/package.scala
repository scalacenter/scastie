package com.olegych.scastie.client

import com.olegych.scastie.api._

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.Reusable
import japgolly.scalajs.react.Callback
import org.scalajs.dom.HTMLElement

package object components {

  val reusableEmpty: Reusable[Callback] = Reusable.always(Callback.empty)

  implicit val reusabilityInputs: Reusability[Inputs] =
    Reusability.byRefOr_==

  implicit val reusabilityUser: Reusability[User] =
    Reusability.byRef || Reusability.derive[User]

  implicit val snippetIdReuse: Reusability[SnippetId] =
    Reusability.byRefOr_==

  implicit val viewReuse: Reusability[View] =
    Reusability.byRefOr_==

  implicit val scalaTargetReuse: Reusability[ScalaTarget] =
    Reusability.byRefOr_==

  implicit val pageReuse: Reusability[Page] =
    Reusability.byRefOr_==

  implicit val scalaTargetTypeReuse: Reusability[ScalaTargetType] =
    Reusability.byRefOr_==

  implicit val scalaScalaDependency: Reusability[ScalaDependency] =
    Reusability.byRefOr_==

  implicit val attachedDomsReuse: Reusability[Map[String, HTMLElement]] =
    Reusability.byRef ||
      Reusability.by(_.keys.toSet)

  implicit val releaseOptionsReuse: Reusability[ReleaseOptions] =
    Reusability.byRefOr_==

  implicit val projectReuse: Reusability[Project] =
    Reusability.byRefOr_==

  implicit val librariesFromReuse: Reusability[Map[ScalaDependency, Project]] =
    Reusability.byRefOr_==

  implicit val instrumentationReuse: Reusability[Set[Instrumentation]] =
    Reusability.byRefOr_==

  implicit val compilationInfosReuse: Reusability[Set[Problem]] =
    Reusability.byRefOr_==

  implicit val runtimeErrorReuse: Reusability[Option[RuntimeError]] =
    Reusability.byRefOr_==

  implicit val consoleOutputsReuse: Reusability[Vector[ConsoleOutput]] =
    Reusability.byRefOr_==

  implicit val snippetSummaryReuse: Reusability[List[SnippetSummary]] =
    Reusability.byRefOr_==

  implicit val consoleStateReuse: Reusability[ConsoleState] =
    Reusability.byRefOr_==

  implicit def reusabilityEventStream[T]: Reusability[EventStream[T]] =
    Reusability.always

  implicit val modalStateReuse: Reusability[ModalState] =
    Reusability.derive[ModalState]

  implicit val snippetStateReuse: Reusability[SnippetState] =
    Reusability.derive[SnippetState]

  implicit val consoleOutputReuse: Reusability[ConsoleOutput] =
    Reusability.byRefOr_==

  implicit val outputsReuse: Reusability[Outputs] =
    Reusability.derive[Outputs]

  implicit val sbtRunnerStateReuse: Reusability[Option[Vector[SbtRunnerState]]] =
    Reusability.byRefOr_==

  implicit val statusStateReuse: Reusability[StatusState] =
    Reusability.derive[StatusState]

  implicit val embeddedOptionsReuse: Reusability[EmbeddedOptions] =
    Reusability.derive[EmbeddedOptions]

  implicit val metalsStatusReuse: Reusability[MetalsStatus] =
    Reusability.byRefOr_==

  implicit val scastieStateReuse: Reusability[ScastieState] =
    Reusability.derive[ScastieState]

  implicit val scastieBackendReuse: Reusability[ScastieBackend] =
    Reusability.byRefOr_==
}
