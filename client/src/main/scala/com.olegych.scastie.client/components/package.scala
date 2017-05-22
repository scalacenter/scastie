package com.olegych.scastie
package client

import api.User

import japgolly.scalajs.react.extra.Reusability

package object components {
  implicit val reusabilityUser: Reusability[User] =
    Reusability.byRef || Reusability.caseClass
}
