package client

import api._

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom
import dom.ext.KeyCode
import dom.raw.{HTMLInputElement, HTMLElement}
import dom.ext.Ajax

import scalajs.concurrent.JSExecutionContext.Implicits.queue

import upickle.default.{read => uread}

import scala.language.higherKinds

object ScaladexSearch {
  // private val scaladexUrl = "http://localhost:8080/api"
  private val scaladexUrl = "https://index.scala-lang.org/api"

  private implicit val projectOrdering =
    Ordering.by { project: Project =>
      (project.organization, project.repository)
    }

  private implicit val scalaDependenciesOrdering =
    Ordering.by { scalaDependency: ScalaDependency =>
      scalaDependency.artifact
    }

  private[ScaladexSearch] case class SearchState(
      query: String = "",
      searchingProjects: List[(Project, String)] = List(),
      // transition state to fetch project details
      projectOptions: Map[(Project, String), ReleaseOptions] = Map(),
      scalaDependencies: List[(Project, ScalaDependency)] = List(),
      selected: Int = 0
  )

  private val projectListRef = Ref[HTMLElement]("projectListRef")
  private val searchInputRef = Ref[HTMLInputElement]("searchInputRef")

  implicit final class ReactExt_DomNodeO[O[_], N <: dom.raw.Node](o: O[N])(
      implicit O: OptionLike[O]) {
    def tryTo(f: HTMLElement => Unit) =
      Callback(O.toOption(o).flatMap(_.domToHtml).foreach(f))
    def tryFocus: Callback = tryTo(_.focus())
  }

  private[ScaladexSearch] class SearchBackend(
      scope: BackendScope[(State, Backend), SearchState]) {
    def keyDown(e: ReactKeyboardEventI): Callback = {

      if (e.keyCode == KeyCode.Down || e.keyCode == KeyCode.Up) {
        val diff =
          if (e.keyCode == KeyCode.Down) +1
          else -1

        def clamp(max: Int, v: Int) =
          if (v >= max) max - 1
          else if (v < 0) 0
          else v

        def interpolate(b: Int, d: Int, x: Int): Double = {
          b.toDouble / d.toDouble * x.toDouble
        }

        def scrollToSelected(selected: Int, total: Int) =
          projectListRef(scope).tryTo(
            el =>
              el.scrollTop =
                Math.abs(interpolate(el.scrollHeight, total, selected + diff)))

        scope.modState(
          s =>
            s.copy(selected = clamp(
              s.searchingProjects.size,
              s.selected + diff))) >> e.preventDefaultCB >> scope.state
          .flatMap(s => scrollToSelected(s.selected, s.searchingProjects.size))

      } else if (e.keyCode == KeyCode.Enter) {
        scope.state.flatMap(
          s =>
            if (0 <= s.selected && s.selected < s.searchingProjects.size)
              addArtifact(s.searchingProjects.toList(s.selected))
            else
              Callback(())) >> searchInputRef(scope).tryFocus
      } else {
        Callback(())
      }
    }

    def addArtifact(artifact: (Project, String))(e: ReactEventI): Callback =
      addArtifact(artifact)

    private def addArtifact(pa: (Project, String)): Callback = {
      val (p, a) = pa
      scope.props.flatMap {
        case (appState, _) =>
          fetchReleaseOptions(p, a, appState.inputs.target)
      }
    }

    def removeScalaDependency(
        project: Project,
        scalaDependency: ScalaDependency)(e: ReactEventI): Callback = {
      scope.modState(
        s =>
          s.copy(scalaDependencies = s.scalaDependencies.filterNot(
            _ == (project -> scalaDependency)))) >> scope.props.flatMap {
        case (_, backend) =>
          backend.removeScalaDependency(scalaDependency)
      }
    }

    def changeVersion(project: Project, scalaDependency: ScalaDependency)(
        e: ReactEventI): Callback = {
      e.extract(_.target.value) { version =>
        scope.modState(
          s =>
            s.copy(
              scalaDependencies =
                project -> scalaDependency.copy(version = version) ::
                  s.scalaDependencies.filterNot(
                    _ == (project -> scalaDependency))
          )) >> scope.props.flatMap {
          case (_, backend) =>
            backend.changeDependencyVersion(scalaDependency, version)
        }
      }
    }

    def selectIndex(index: Int)(e: ReactEventI): Callback = {
      scope.modState(s => s.copy(selected = index))
    }

    def setQuery(e: ReactEventI): Callback = {
      e.extract(_.target.value) { value =>
        scope.modState(_.copy(query = value, selected = 0), fetchProjects())
      }
    }

    def filterAddedDependencies(state: SearchState) = {
      val added =
        state.scalaDependencies.map {
          case (p, s) => (p, s.artifact)
        }.toSet

      state.copy(searchingProjects = state.searchingProjects.filter(s =>
        !added.contains(s)))
    }

    private def fetchProjects(): Callback = {
      def fetch(appState: State, searchState: SearchState): Callback = {
        if (!searchState.query.isEmpty) {
          val query = toQuery(
            Map("q" -> searchState.query) ++
              appState.inputs.target.scaladexRequest
          )

          Callback.future(
            Ajax
              .get(scaladexUrl + "/search" + query)
              .map(ret => uread[List[Project]](ret.responseText))
              .map { projects =>
                val artifacts = projects.flatMap(project =>
                  project.artifacts.map(a => (project, a)))
                scope.modState(
                  s =>
                    filterAddedDependencies(
                      s.copy(searchingProjects = artifacts)))
              }
          )
        } else scope.modState(_.copy(searchingProjects = List()))
      }

      for {
        props <- scope.props
        (appState, _) = props
        searchState <- scope.state
        _ <- fetch(appState, searchState)
      } yield ()
    }

    private def toQuery(in: Map[String, String]): String =
      in.map { case (k, v) => s"$k=$v" }.mkString("?", "&", "")

    private def fetchReleaseOptions(project: Project,
                                    artifact: String,
                                    target: ScalaTarget): Callback = {
      val query =
        toQuery(
          Map(
            "organization" -> project.organization,
            "repository" -> project.repository
          ))

      Callback.future(
        Ajax
          .get(scaladexUrl + "/project" + query)
          .map(ret => uread[ReleaseOptions](ret.responseText))
          .map { options =>
            val scalaDependency = ScalaDependency(options.groupId,
                                                  artifact,
                                                  target,
                                                  options.versions.last)
            scope.modState(
              s =>
                filterAddedDependencies(s.copy(
                  projectOptions = s.projectOptions + ((project, artifact) -> options),
                  scalaDependencies = ((project, scalaDependency)) :: s.scalaDependencies
                ))) >> scope.props.flatMap {
              case (_, backend) =>
                backend.addScalaDependency(scalaDependency)
            }
          }
      )
    }
  }

  private val component = ReactComponentB[(State, Backend)]("Scaladex Search")
    .initialState(SearchState())
    .backend(new SearchBackend(_))
    .renderPS {
      case (scope, (state, backend), searchState) =>
        def selectedIndex(index: Int, selected: Int) = {
          if (index == selected) TagMod(`class` := "selected")
          else EmptyTag
        }

        def renderProject(project: Project,
                          artifact: String,
                          selected: TagMod = EmptyTag,
                          handlers: TagMod = EmptyTag,
                          remove: TagMod = EmptyTag,
                          options: TagMod = EmptyTag) = {
          import project._

          val common = TagMod(title := organization, `class` := "logo")
          val artifact2 =
            artifact
              .replaceAllLiterally(project.repository + "-", "")
              .replaceAllLiterally(project.repository, "")

          val label =
            if (project.repository != artifact)
              s"${project.repository} / $artifact2"
            else artifact

          val scaladexLink =
            s"https://index.scala-lang.org/$organization/$repository/$artifact"

          li(selected)(
            a(`class` := "scaladex", href := scaladexLink, target := "_blank")(
              iconic.externalLink
            ),
            remove,
            span(handlers)(
              logo
                .map(
                  url =>
                    img(src := url,
                        common,
                        alt := s"$organization logo or avatar"))
                .getOrElse(
                  img(src := "/assets/placeholder.svg",
                      common,
                      alt := s"placeholder for $organization")
                ),
              span(`class` := "artifact")(label)
            ),
            options
          )
        }

        def renderOptions(project: Project, scalaDependency: ScalaDependency) = {
          searchState.projectOptions.get((project, scalaDependency.artifact)) match {
            case Some(options) =>
              select(value := scalaDependency.version,
                     onChange ==> scope.backend
                       .changeVersion(project, scalaDependency))(
                options.versions.reverse.map(v => option(value := v)(v))
              )
            case None => EmptyTag
          }
        }

        val added = {
          val hideAdded =
            if (searchState.scalaDependencies.isEmpty) TagMod(display.none)
            else EmptyTag

          ol(`class` := "added", hideAdded)(
            searchState.scalaDependencies.toList.map {
              case (p, d) =>
                renderProject(
                  p,
                  d.artifact,
                  remove = iconic.x(
                    `class` := "remove",
                    onClick ==> scope.backend.removeScalaDependency(p, d)
                  ),
                  options = renderOptions(p, d))
            })
        }

        fieldset(`class` := "scaladex")(
          legend("Scala Libraries"),
          div(`class` := "search")(
            added,
            input.search(
              ref := searchInputRef,
              placeholder := "Search for 'fp'",
              value := searchState.query,
              onChange ==> scope.backend.setQuery,
              onKeyDown ==> scope.backend.keyDown
            ),
            ol(`class` := "results", ref := projectListRef)(
              searchState.searchingProjects.zipWithIndex.toList.map {
                case ((project, artifact), index) =>
                  renderProject(
                    project,
                    artifact,
                    selected = selectedIndex(index, searchState.selected),
                    handlers = TagMod(
                      onClick ==> scope.backend.addArtifact(
                        (project, artifact)),
                      onMouseOver ==> scope.backend.selectIndex(index)
                    ))
              })
          )
        )
    }
    .componentDidMount(s => searchInputRef(s).tryFocus)
    .build

  def apply(state: State, backend: Backend) = component((state, backend))
}
