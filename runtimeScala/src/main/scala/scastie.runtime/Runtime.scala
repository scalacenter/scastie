package scastie.runtime
import api._

import upickle.default.{write => uwrite}

object Runtime {
  def write(instrumentations: List[Instrumentation]): String = {
    uwrite(instrumentations)
  }
  def render[T: pprint.PPrint](a: T)(implicit tp: pprint.TPrint[T]): Render = {
    import pprint.Config.Defaults._
    a match {
      case md: Markdown ⇒ md
      case html: Html   ⇒ html
      case v            ⇒ Value(pprint.tokenize(v).mkString, tp.render)
    }
  }
}
