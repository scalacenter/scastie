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
    def keyDown(e: ReactEventI) = {
      Callback(())
    }

    def setQuery(e: ReactEventI) = {
      def fetchProjects(queryIn: String, appState: State): Callback = { //: CallbackTo[List[Project]] = {
        if(!queryIn.isEmpty) {
          val query = 
            (Map("q" -> queryIn) ++ appState.inputs.target.scaladexRequest).map{ 
              case (k, v) => s"$k=$v" 
            }.mkString("?", "&", "")

          Callback.future(
            Ajax.get("http://localhost:8080/api/scastie" + query).map{ ret =>
              uread[List[Project]](ret.responseText)
            }.map(projects => scope.modState(_.copy(projects = projects)))
          )
        } else scope.modState(_.copy(projects = Nil))
      }

      e.extract(_.target.value)(value =>
        for {
          _ <- scope.modState(_.copy(query = value))
          props <- scope.props
          (appState, _) = props
          _ <- fetchProjects(value, appState)
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
            onChange ==> scope.backend.setQuery,
            onKeyDown ==> scope.backend.keyDown
          ),
          ul(searchState.projects.map{ case Project(organization, repository, logo, artifacts) =>
            li(
              logo.map(url => img(src := url, alt := "project logo")).getOrElse(EmptyTag),
              span(s"$organization /$repository"),
              ul(artifacts.map(artifact =>
                li(artifact)
              ))
            )
          })
        )
      )
    }
    .build

  def apply(state: State, backend: Backend) = component((state, backend))
}