package com.olegych.scastie.client

import org.scalatest.FunSpec

import japgolly.scalajs.react._, vdom.html_<^._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.test._

object NameChangerSpec extends FunSpec {

  val NameChanger = ScalaComponent
    .builder[StateSnapshot[String]]("Name changer")
    .render_P { ss =>
      def updateName =
        (event: ReactEventFromInput) => ss.setState(event.target.value)
      <.input.text(^.value := ss.value, ^.onChange ==> updateName)
    }
    .build

  describe("name changer") {
    it("render based on props") {
      val nameVar = ReactTestVar("guy")
      ReactTestUtils.withRenderedIntoDocument(
        NameChanger(nameVar.stateSnapshot())
      ) { m =>
        SimEvent.Change("bob").simulate(m)
        assert(nameVar.value() == "bob")
      }
    }
  }
}
