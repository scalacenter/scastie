package com.olegych.scastie.client

import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.test.ReactTestUtils
import org.scalatest.{BeforeAndAfterAll, FunSpec}
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.jquery


class HelloWorldSpec extends FunSpec with BeforeAndAfterAll {

  describe("world") {
    it("should be greeted") {
      val message = HelloMessage("World")
      val comp = ReactTestUtils.renderIntoDocument(message)
      val div = ReactTestUtils.findRenderedDOMComponentWithClass(comp, "abc")

      val text = jquery.jQuery(div).text()
      assert(text == "Hello World")
    }
  }

  val HelloMessage = ReactComponentB[String]("HelloMessage")
    .render($ => <.div(^.className := "abc", "Hello ", $.props))
    .build


}
