package com.olegych.scastie.client

import com.olegych.scastie.api._

import japgolly.scalajs.react.extra.Reusability
import japgolly.scalajs.react.extra.Reusable
import japgolly.scalajs.react.Callback

package object components {

  val reusableEmpty: Reusable[Callback] = Reusable.always(Callback.empty)

  implicit val reusabilityInputs: Reusability[Inputs] =
    Reusability.byRefOr_==

  implicit val reusabilityUser: Reusability[User] =
    Reusability.byRef || Reusability.caseClass[User]

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

  implicit val attachedDomsReuse: Reusability[AttachedDoms] =
    Reusability.byRef ||
      Reusability.by(_.v.keys.toSet)

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
    Reusability.caseClass[ModalState]

  implicit val snippetStateReuse: Reusability[SnippetState] =
    Reusability.caseClass[SnippetState]

  implicit val consoleOutputReuse: Reusability[ConsoleOutput] =
    Reusability.byRefOr_==

  implicit val outputsReuse: Reusability[Outputs] =
    Reusability.caseClass[Outputs]

  implicit val sbtRunnerStateReuse: Reusability[Option[Vector[SbtRunnerState]]] =
    Reusability.byRefOr_==

  implicit val statusStateReuse: Reusability[StatusState] =
    Reusability.caseClass[StatusState]

  implicit val embeddedOptionsReuse: Reusability[EmbeddedOptions] =
    Reusability.caseClass[EmbeddedOptions]

  implicit val scastieStateReuse: Reusability[ScastieState] =
    Reusability.caseClass[ScastieState]

  implicit val scastieBackendReuse: Reusability[ScastieBackend] =
    Reusability.byRefOr_==
}
