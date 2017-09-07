package com.olegych.scastie.client.components.editor

import codemirror.{TextMarker, LineWidget}

private[editor] sealed trait Annotation {
  def clear(): Unit
}

private[editor] case class Line(lw: LineWidget) extends Annotation {
  def clear(): Unit = lw.clear()
}

private[editor] case class Marked(tm: TextMarker) extends Annotation {
  def clear(): Unit = tm.clear()
}

private[editor] case object Empty extends Annotation {
  def clear(): Unit = ()
}