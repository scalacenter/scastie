package client

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom
import dom.ext.Ajax
import dom.ext.KeyCode
import dom.raw.HTMLInputElement

import scalajs.concurrent.JSExecutionContext.Implicits.queue

import upickle.default.{read ⇒ uread}

object ScaladexSearch {

  private[ScaladexSearch] case class SearchState(
    query: String = "",
    projects: List[Project] = List(),
    addedProjects: Set[Project] = Set(),
    selected: Int = 0
  )

  private val searchInputRef = Ref[HTMLInputElement]("searchInputRef")

  private[ScaladexSearch] class SearchBackend(scope: BackendScope[(State, Backend), SearchState]) {
    def keyDown(e: ReactKeyboardEventI): Callback = {

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
        scope.modState{s =>
          if(0 <= s.selected && s.selected < s.projects.size) {
            s.copy(addedProjects = s.addedProjects + s.projects(s.selected))
          }
          else s
        } >> searchInputRef(scope).tryFocus
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
        } else scope.modState(_.copy(projects = List()))
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

      def renderProject(project: Project, mod: TagMod = EmptyTag) = {
        val Project(organization, repository, logo) = project

        li(mod)(
          logo.map(url => 
            img(src := url, alt := s"$organization logo or avatar")
          ).getOrElse(
            img(src := "/assets/placeholder.svg", alt := s"placeholder for $organization")
          ),
          span(repository)
        )
      }

      val added = 
        if(!searchState.addedProjects.isEmpty) 
          ul(`class` := "added")(searchState.addedProjects.toList.map(p => renderProject(p)))
        else EmptyTag

      fieldset(`class` := "scaladex")(
        legend("Scala Libraries"),

        div(`class` := "search")(
          added,
          input.search(
            ref := searchInputRef,
            placeholder := "Search for 'time'",
            value := searchState.query,
            onChange ==> scope.backend.setQuery,
            onKeyDown ==> scope.backend.keyDown
          ),
          ul(searchState.projects.zipWithIndex.map{ case (project, index) =>
            renderProject(project, selected(index, searchState.selected))
          })
        )
      )
    }
    .componentDidMount(s => searchInputRef(s).tryFocus)
    .build

  def apply(state: State, backend: Backend) = component((state, backend))
}