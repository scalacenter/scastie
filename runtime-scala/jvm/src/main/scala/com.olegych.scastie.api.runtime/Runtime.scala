package com.olegych.scastie.api
package runtime

object Runtime extends SharedRuntime {
  override def render[T: pprint.PPrint](a: T)(implicit tp: pprint.TPrint[T]): Render = {
    super.render(a)
  }
}
