package client

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom
import dom.ext.KeyCode
import dom.raw.HTMLInputElement
import dom.ext.Ajax

import scalajs.concurrent.JSExecutionContext.Implicits.queue

import upickle.default.{read ⇒ uread}

import scala.collection.immutable.SortedSet

object ScaladexSearch {

  private implicit def ordering = new Ordering[(Project, String)]{
    private val cmp  = implicitly[Ordering[String]]
    def compare(a: (Project, String), b: (Project, String)): Int = {
      cmp.compare(a._2, b._2)
    }
  }

  private[ScaladexSearch] case class SearchState(
    query: String = "",
    projects: SortedSet[(Project, String)] = SortedSet(),
    addedProjects: SortedSet[(Project, String)] = SortedSet(),
    projectOptions: Map[Project, ReleaseOptions] = Map(),
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
        scope.state.flatMap( s =>
          if(0 <= s.selected && s.selected < s.projects.size)
            addArtifact(s.projects.toList(s.selected))
          else 
            Callback(())
        ) >> searchInputRef(scope).tryFocus
      } else {
        Callback(())
      }
    }

    def addArtifact(artifact: (Project, String))(e: ReactEventI): Callback = {
      addArtifact(artifact)
    }

    def removeArtifact(project: Project, artifact: String)(e: ReactEventI): Callback = {
      scope.modState(s => s.copy(addedProjects = s.addedProjects - (project -> artifact)), fetchProjects())
    }
    
    def selectIndex(index: Int)(e: ReactEventI): Callback = {
      scope.modState(s => s.copy(selected = index))
    }

    def setQuery(e: ReactEventI): Callback = {
      e.extract(_.target.value){ value => 
        scope.modState(_.copy(query = value, selected = 0), fetchProjects())
      }
    }

    private def addArtifact(pa: (Project, String)): Callback = {
      val (p, a) = pa
      scope.modState(s =>
        s.copy(addedProjects = s.addedProjects + pa),
        fetchProjects() >> fetchReleaseOptions(p)
      )
    }

    private val scaladexUrl = "http://localhost:8080/api/scastie"

    private def toQuery(in: Map[String, String]): String =
      in.map{ case (k, v) => s"$k=$v" }.mkString("?", "&", "")

    private def fetchReleaseOptions(project: Project): Callback = {
      val query = 
        toQuery(Map(
          "organization" -> project.organization,
          "repository" -> project.repository
        ))

      Callback.future(
        Ajax.get(scaladexUrl + "/project" + query).map(ret =>
          uread[ReleaseOptions](ret.responseText)
        ).map(options =>
          scope.modState(s => s.copy(projectOptions = s.projectOptions + (project -> options)))
        )
      )
    }

    private def fetchProjects(): Callback = {
      def fetch(appState: State, searchState: SearchState): Callback = {
        if(!searchState.query.isEmpty) {
          val query = toQuery(Map("q" -> searchState.query) ++ appState.inputs.target.scaladexRequest)

          Callback.future(
            Ajax.get(scaladexUrl + "/search" + query).map( ret =>
              uread[List[Project]](ret.responseText)
            ).map{ projects =>            
              val artifacts = projects.flatMap(project => project.artifacts.map(a => (project, a))).to[SortedSet]
              val filtered = (artifacts -- searchState.addedProjects).take(10)
              scope.modState(_.copy(projects = filtered))
            }
          )
        } else scope.modState(_.copy(projects = SortedSet()))
      }

      for {
        props <- scope.props
        (appState, _) = props
        searchState <- scope.state
        _ <- fetch(appState, searchState)
      } yield ()
    }
  }

  private val component = ReactComponentB[(State, Backend)]("Scaladex Search")
    .initialState(SearchState())
    .backend(new SearchBackend(_))
    .renderPS { case (scope, (state, backend), searchState) =>

      def selectedIndex(index: Int, selected: Int) = {
        if(index == selected) TagMod(`class` := "selected")
        else EmptyTag
      }

      def renderProject(project: Project, artifact: String, 
        selected: TagMod = EmptyTag,
        handlers: TagMod = EmptyTag,
        remove: TagMod = EmptyTag,
        options: TagMod = EmptyTag) = {
        import project._

        val common = TagMod(title := organization, `class` := "logo")
        val artifact2 = 
          artifact.replaceAllLiterally(project.repository + "-", "")
                  .replaceAllLiterally(project.repository, "")

        val label =
          if(project.repository != artifact) s"${project.repository} / $artifact2"
          else artifact

        val scaladexLink =
          s"https://index.scala-lang.org/$organization/$repository/$artifact"

        li(selected)(
          a(`class` := "scaladex", href := scaladexLink, target := "_blank")(
            iconic.externalLink
          ),
          remove,
          span(handlers)(
            logo.map(url => 
              img(src := url, common, alt := s"$organization logo or avatar")
            ).getOrElse(
              img(src := "/assets/placeholder.svg", common, alt := s"placeholder for $organization")
            ),
            span(`class` := "artifact")(label)
          ),
          options
        )
      }

      def renderOptions(project: Project) = {
        dom.console.log(project.toString)
        dom.console.log(searchState.projectOptions.toString)
        searchState.projectOptions.get(project) match {
          case Some(options) =>
            select(
              options.versions.reverse.map(v =>
                option(selected := v == options.version)(v)
              )
            )
          case None => EmptyTag
        }
      }

      val added = {
        val hideAdded = 
          if(searchState.addedProjects.isEmpty) TagMod(display.none)
          else EmptyTag

        ol(`class` := "added", hideAdded)(searchState.addedProjects.toList.map{ case(p, a) => 
          renderProject(p, a, 
            remove = iconic.x(
              `class` := "remove",
              onClick ==> scope.backend.removeArtifact(p, a)
            ),
            options = renderOptions(p)
          )
        })
      }
       
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
          ol(searchState.projects.zipWithIndex.toList.map{ case ((project, artifact), index) =>
            renderProject(project, artifact, 
              selected = selectedIndex(index, searchState.selected),
              handlers = TagMod(
                onClick ==> scope.backend.addArtifact((project, artifact)),
                onMouseOver ==> scope.backend.selectIndex(index)
              )
            )
          })
        )
      )
    }
    .componentDidMount(s => searchInputRef(s).tryFocus)
    .build

  def apply(state: State, backend: Backend) = component((state, backend))
}