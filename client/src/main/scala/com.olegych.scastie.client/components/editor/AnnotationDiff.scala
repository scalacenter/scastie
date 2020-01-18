package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react.{Callback, CallbackTo}

object AnnotationDiff {
  def setAnnotations[T](
      currentProps: Option[Editor],
      nextProps: Editor,
      state: EditorState,
      modState: (EditorState => EditorState) => Callback,
      fromPropsAndState: (Editor, EditorState) => Set[T],
      annotate: T => Annotation,
      fromState: EditorState => Map[T, Annotation],
      updateState: (EditorState, Map[T, Annotation]) => EditorState
  ): Callback = {

    val currentAnnotations: Set[T] =
      currentProps
        .map(props => fromPropsAndState(props, state))
        .getOrElse(Set())

    val nextAnnotations: Set[T] =
      fromPropsAndState(nextProps, state)

    val addedAnnotations: Set[T] =
      nextAnnotations -- currentAnnotations

    val annotationsToAdd: CallbackTo[Map[T, Annotation]] =
      CallbackTo
        .sequence(
          addedAnnotations.map(item => CallbackTo((item, annotate(item))))
        )
        .map(_.toMap)

    val removedAnnotations: Set[T] =
      currentAnnotations -- nextAnnotations

    val annotationsToRemove: CallbackTo[Set[T]] =
      CallbackTo.sequence(
        fromState(state).view
          .filterKeys(removedAnnotations.contains)
          .map {
            case (item, annot) => CallbackTo({ annot.clear(); item })
          }
          .toSet
      )

    for {
      added <- annotationsToAdd
      removed <- annotationsToRemove
      _ <- modState { state =>
        updateState(state, (fromState(state) ++ added) -- removed)
      }
    } yield ()
  }
}
