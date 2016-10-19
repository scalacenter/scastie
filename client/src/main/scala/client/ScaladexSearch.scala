package client

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom
import dom.ext.Ajax
import dom.ext.KeyCode
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import upickle.default.{read ⇒ uread}

object ScaladexSearch {

  private[ScaladexSearch] case class SearchState(
    query: String = "",
    projects: List[Project] = Nil,
    addedProjects: List[Project] = Nil,
    selected: Int = 0
  )

  private[ScaladexSearch] class SearchBackend(scope: BackendScope[(State, Backend), SearchState]) {
    def keyDown(e: ReactKeyboardEventI) = {

      if(e.keyCode == KeyCode.Down || e.keyCode == KeyCode.Up) {
        val diff = 
          if(e.keyCode == KeyCode.Down) +1
          else                          -1

        def clamp(max: Int, v: Int) = 
          if(v >= max) max - 1
          else if(v < 0) 0
          else v

        scope.modState(s => 
          s.copy(selected = clamp(s.projects.size, s.selected + diff))
        ) >> e.preventDefaultCB

      } else if(e.keyCode == KeyCode.Enter) {
        scope.modState(s =>
          if(0 >= s.selected && s.selected < s.projects.length)
            s.copy(addedProjects = s.projects(s.selected) :: s.addedProjects)
          else s
        )
      } else {
        Callback(())
      }
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
          _ <- scope.modState(_.copy(query = value, selected = 0))
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
      def selected(index: Int, selected: Int) = {
        if(index == selected) TagMod(`class` := "selected")
        else EmptyTag
      }

      div(`class` := "scaladex")(
        fieldset(
          legend("Scala Libraries"),
          input.search(
            placeholder := "Search for 'time'",
            value := searchState.query,
            onChange ==> scope.backend.setQuery,
            onKeyDown ==> scope.backend.keyDown
          ),
          ul(searchState.projects.zipWithIndex.map{ case (Project(organization, repository, logo), index) =>
            li(selected(index, searchState.selected))(
              logo.map(url => 
                img(src := url, alt := "project logo")
              ).getOrElse(
                img(src := "/assets/placeholder.svg", alt := "project logo placeholder")
              ),
              span(s"$organization / $repository")
            )
          })
        ),
        ul(`class` := "added")(searchState.addedProjects.map{ case Project(organization, repository, logo) =>
          li(
            logo.map(url => 
              img(src := url, alt := "project logo")
            ).getOrElse(
              img(src := "/assets/placeholder.svg", alt := "project logo placeholder")
            ),
            span(s"$organization / $repository")
          )
        })

      )
    }
    .build

  def apply(state: State, backend: Backend) = component((state, backend))
}