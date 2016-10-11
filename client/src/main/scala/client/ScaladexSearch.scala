package client

import App._

import japgolly.scalajs.react._, vdom.all._

object ScaladexSearch {

  // input

  // libs list

  private val component = ReactComponentB[(State, Backend)]("Scaladex Search")
    .render_P { case (state, backend) =>
      // import backend._

      



      div(
        // input
        // libs list
      )
    }
    .build
  def apply(state: State, backend: Backend) = component((state, backend))
}