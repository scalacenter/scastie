package org.scastie.runtime

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import org.scastie.runtime.api._

object Runtime extends JvmRuntime {
  def render[T](a: T)(implicit _ct: ClassTag[T] = null, _tt: TypeTag[T] = null): Render = {
    val ct = Option(_ct)
    val tt = Option(_tt)
    super.render(a, tt.map(_.tpe.toString).orElse(ct.map(_.toString)).getOrElse(""))
  }
}
