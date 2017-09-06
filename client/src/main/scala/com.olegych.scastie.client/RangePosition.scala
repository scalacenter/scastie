package com.olegych.scastie.client

import play.api.libs.json._

case class RangePosititon(indexStart: Int, indexEnd: Int)

object RangePosititon {
  implicit val formatRangePostiton: OFormat[RangePosititon] =
    Json.format[RangePosititon]
}