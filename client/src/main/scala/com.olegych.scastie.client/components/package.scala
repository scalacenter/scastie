package com.olegych.scastie.client

import com.olegych.scastie.proto.User

import japgolly.scalajs.react.extra.Reusability

package object components {
  implicit val reusabilityUser: Reusability[User] =
    Reusability.byRef || Reusability.caseClass
}
