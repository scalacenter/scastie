package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react.extra.Reusability

private[editor] case class RangePosititon(indexStart: Int, indexEnd: Int)

object RangePosititon {
  implicit val rangePosititonReuse: Reusability[RangePosititon] =
    Reusability.byRefOr_==
}
