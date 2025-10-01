package org.scastie.runtime

import scala.quoted.*

import org.scastie.runtime.api.Render

object Runtime extends JvmRuntime:
  inline def render[T](a: T): Render = ${ _render('a) }

  private def _render[T: Type](a: Expr[T])(
    using Quotes
  ): Expr[Render] =
    import quotes.reflect.*
    val t = TypeRepr.of[T]
    '{ Runtime.render($a, ${ Expr(t.show) }) }
