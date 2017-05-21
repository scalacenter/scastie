package com.olegych.scastie.client

import com.olegych.scastie.api._
import japgolly.scalajs.react.extra.Reusability

object ApiReusability {

  implicit val reusabilityUser: Reusability[User] =
    Reusability.byRef || Reusability.caseClass

}
