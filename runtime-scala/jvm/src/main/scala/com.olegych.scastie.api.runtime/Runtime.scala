package com.olegych.scastie.api
package runtime

import upickle.default.{write => uwrite}

object Runtime extends SharedRuntime {
  def render[T: pprint.PPrint](a: T)(implicit tp: pprint.TPrint[T]): Render = {
    super.render(a)
  }
}
