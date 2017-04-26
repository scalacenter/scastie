package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._, test._

import org.scalatest.{BeforeAndAfterAll, FunSpec}

class ExampleSpec extends FunSpec with BeforeAndAfterAll {

  describe("Example") {
    it("render based on props") {
      ComponentTester(Example)("First props") { tester =>
        import tester._

        def assertHtml(p: String, s: Int): Unit = {
          assert(
            component.outerHtmlWithoutReactInternals() == s"<div> $p:$s </div>"
          )
          ()
        }

        assertHtml("First props", 0)

        setState(2)
        assertHtml("First props", 2)

        setProps("Second props")
        assertHtml("Second props", 2)
      }
    }
  }

  val Example = ScalaComponent.builder[String]("Example")
    .initialState(0)
    .renderPS((_, p, s) => div(s" $p:$s "))
    .build
}
