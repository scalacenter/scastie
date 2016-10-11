package client

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom
import dom.ext.Ajax
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import upickle.default.{read ⇒ uread}

object ScaladexSearch {

  private[ScaladexSearch] case class SearchState(
    query: String = "",
    projects: List[Project] = Nil
  )

  private[ScaladexSearch] class SearchBackend(scope: BackendScope[(State, Backend), SearchState]) {
    def setQuery(e: ReactEventI) = {

      def fetchProjects(searchState: SearchState, appState: State): Callback = { //: CallbackTo[List[Project]] = {
        val query = 
          (Map("q" -> searchState.query) ++ appState.inputs.target.scaladexRequest).map{ 
            case (k, v) => s"$k=$v" 
          }.mkString("?", "&", "")

        Callback.future(
          Ajax.get("http://localhost:8080/api/scastie" + query).map{ ret =>
            uread[List[Project]](ret.responseText)
          }.map(projects => scope.modState(_.copy(projects = projects)))
        )
      }
      e.extract(_.target.value)(value =>
        for {
          _ <- scope.modState(_.copy(query = value))
          props <- scope.props
          (appState, _) = props
          state <- scope.state
          _ <- fetchProjects(state, appState)
        } yield ()
      )
    }
  }

  private val component = ReactComponentB[(State, Backend)]("Scaladex Search")
    .initialState(SearchState())
    .backend(new SearchBackend(_))
    .renderPS { case (scope, (state, backend), searchState) =>
      li(`class` := "scaladex")(
        fieldset(
          legend("Scala Library"),
          input.search(
            placeholder := "Search for 'time'",
            value := searchState.query,
            onChange ==> scope.backend.setQuery
          ),
          ul(searchState.projects.map(p =>
            li(p.toString)
          ))
        )
      )
    }
    .build

  def apply(state: State, backend: Backend) = component((state, backend))
}