package com.olegych.scastie.client

import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.test.ReactTestUtils
import org.scalatest.FunSpec
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.jquery


class TopBarSpec extends FunSpec {

  describe("hello") {
    it("should do it") {
      val message = HelloMessage("Armin")
      val comp = ReactTestUtils.renderIntoDocument(message)
      val div = ReactTestUtils.findRenderedDOMComponentWithClass(comp, "abc")

      println(s"thing: $div ")
      val text = jquery.jQuery(div).text()
      //      println(s"thing: ${DebugJs inspectObject text}")
      assert(text == "Hello Armin")
    }
  }

  val HelloMessage = ReactComponentB[String]("HelloMessage")
    .render($ => <.div(^.className := "abc", "Hello ", $.props))
    .build


//  val tests = TestSuite {
//    //    'hello {
//    //      'world {
//    //        val x = 1
//    //        val y = 2
//    //        assert(x != y)
//    //        (x, y)
//    //      }
//    //    }
//    //    'test2 {
//    //      val a = 1
//    //      val b = 1
//    //      assert(a == b)
//
//    'testPostFunction {
//      assert(true)
//
//    }
//
//    'test3 {
//      val message = HelloMessage("Armin")
//      val comp = ReactTestUtils.renderIntoDocument(message)
//      val div = ReactTestUtils.findRenderedDOMComponentWithClass(comp, "abc")
//
//      println(s"thing: $div ")
//      val text = jQuery(div).text()
//      //      println(s"thing: ${DebugJs inspectObject text}")
//      assert(text == "Hello Armin")
//    }
//
//  }
}
