package com.olegych.scastie.client.components.editor

/**
 *    +------------------------+-------------+
 *    v                        |             |
 *   Idle --> Requested --> Active <--> NeedRender
 *    ^           |
 *    +-----------+
 *   only if exactly one
 *   completion returned
 */
private[editor] sealed trait CompletionState
private[editor] case object Idle extends CompletionState
private[editor] case object Requested extends CompletionState
private[editor] case object Active extends CompletionState
private[editor] case object NeedRender extends CompletionState
