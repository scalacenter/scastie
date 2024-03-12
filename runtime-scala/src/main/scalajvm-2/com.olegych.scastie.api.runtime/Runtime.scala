package com.olegych.scastie.api
package runtime

import scala.reflect.runtime.universe._
import scala.reflect.ClassTag

object Runtime extends JvmRuntime {

  def render[T](a: T)(
    implicit _ct: ClassTag[T] = null,
    _tt: TypeTag[T] = null
  ): Render = {
    val ct = Option(_ct)
    val tt = Option(_tt)
    super.render(a, tt.map(_.tpe.toString).orElse(ct.map(_.toString)).getOrElse(""))
  }

}
