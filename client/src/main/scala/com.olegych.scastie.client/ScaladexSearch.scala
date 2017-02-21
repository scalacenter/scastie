package com.olegych.scastie
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
  private[ScaladexSearch] object SearchState {
    def default = SearchState(
      query = "",
      searchingProjects = List(),
      projectOptions = Map(),
      scalaDependencies = List(),
      selected = 0
    )

    def fromProps(props: (State, Backend)): SearchState = {
      // val (state, _) = props
      // state.searchState
      SearchState.default
    }
  }

  private[ScaladexSearch] case class SearchState(
      query: String,
      searchingProjects: List[(Project, String)],
      projectOptions: Map[(Project, String), ReleaseOptions], // transition state to fetch project details
      scalaDependencies: List[(Project, ScalaDependency)],
      selected: Int
  ) {
    def addScalaDependency(project: Project, scalaDependency: ScalaDependency, options: ReleaseOptions): SearchState = {
      copy(
        projectOptions = projectOptions + ((project, scalaDependency.artifact) -> options),
        scalaDependencies = ((project, scalaDependency)) :: scalaDependencies
      ).removedAddedDependencies
    }

    private def removedAddedDependencies: SearchState = {
      val added = scalaDependencies.map { case (p, s) => (p, s.artifact)}.toSet
      copy(
        searchingProjects = searchingProjects.filter(s => !added.contains(s))
      )
    }              

    def removeDependency(project: Project, scalaDependency: ScalaDependency): SearchState =
      copy(
        scalaDependencies = 
          scalaDependencies.filterNot(_ == (project -> scalaDependency))
      )

    def updateVersion(project: Project, scalaDependency: ScalaDependency, version: String): SearchState = {
      val state0 = removeDependency(project, scalaDependency)
      state0.copy(
        scalaDependencies = (project -> scalaDependency.copy(version = version)) :: state0.scalaDependencies
      )
    }

    def addProjects(projects: List[Project]): SearchState = {
      val artifacts = projects.flatMap(project => project.artifacts.map(artifact => (project, artifact)))
      copy(searchingProjects = artifacts).removedAddedDependencies
    }

    def clearProjects: SearchState = {
      copy(searchingProjects = List())
    }
  }

  // private val scaladexBaseUrl = "http://localhost:8080"
  private val scaladexBaseUrl = "https://index.scala-lang.org"
  private val scaladexApiUrl = scaladexBaseUrl + "/api"

  private implicit val projectOrdering =
    Ordering.by { project: Project =>
      (project.organization, project.repository)
    }

  private implicit val scalaDependenciesOrdering =
    Ordering.by { scalaDependency: ScalaDependency =>
      scalaDependency.artifact
    }

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

        def selectProject =
          scope.modState(s =>
            s.copy(selected = clamp(s.searchingProjects.size, s.selected + diff))
          )

        def scrollToSelectedProject =
          scope.state.flatMap(s =>
            scrollToSelected(s.selected, s.searchingProjects.size))

        selectProject >>
        e.preventDefaultCB >>
        scrollToSelectedProject

      } else if (e.keyCode == KeyCode.Enter) {

        def addArtifactIfInRange =
          scope.state.flatMap(s =>
            if (0 <= s.selected && s.selected < s.searchingProjects.size)
              addArtifact(s.searchingProjects.toList(s.selected))
            else
              Callback(())
          )

        addArtifactIfInRange >> searchInputRef(scope).tryFocus
      } else {
        Callback(())
      }
    }

    def addArtifact(projectAndArtifact: (Project, String))(e: ReactEventI): Callback =
      addArtifact(projectAndArtifact)

    private def addArtifact(projectAndArtifact: (Project, String)): Callback = {
      val (project, artifact) = projectAndArtifact
      scope.props.flatMap {
        case (appState, _) =>
          fetchReleaseOptions(project, artifact, appState.inputs.target)
      }
    }

    def removeScalaDependency(
        project: Project,
        scalaDependency: ScalaDependency)(e: ReactEventI): Callback = {

      def removeDependencyLocal =
        scope.modState(_.removeDependency(project, scalaDependency))

      def removeDependecyBackend =
        scope.props.flatMap { case (_, backend) =>
          backend.removeScalaDependency(scalaDependency)
        }

      removeDependencyLocal >> removeDependecyBackend
    }

    def updateVersion(project: Project, scalaDependency: ScalaDependency)(
        e: ReactEventI): Callback = {

      e.extract(_.target.value) { version =>

        def updateDependencyVersionLocal =
          scope.modState(_.updateVersion(project, scalaDependency, version))

        def updateDependencyVersionBackend =
          scope.props.flatMap {
            case (_, backend) =>
              backend.updateDependencyVersion(scalaDependency, version)
          }

        updateDependencyVersionLocal >> updateDependencyVersionBackend
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

    private def fetchProjects(): Callback = {
      def fetch(appState: State, searchState: SearchState): Callback = {
        if (!searchState.query.isEmpty) {
          val query = toQuery(
            Map("q" -> searchState.query) ++
              appState.inputs.target.scaladexRequest
          )

          Callback.future(
            Ajax
              .get(scaladexApiUrl + "/search" + query)
              .map(ret => uread[List[Project]](ret.responseText))
              .map(projects => scope.modState(_.addProjects(projects)))
          )
        } else scope.modState(_.clearProjects)
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
          ) ++ target.scaladexRequest
        )

      Callback.future(
        Ajax
          .get(scaladexApiUrl + "/project" + query)
          .map(ret => uread[ReleaseOptions](ret.responseText))
          .map { options =>

            val scalaDependency = 
              ScalaDependency(
                options.groupId,
                artifact,
                target,
                options.versions.last
              )

            def addScalaDependencyLocal =
              scope.modState(_.addScalaDependency(project, scalaDependency, options))

            def addScalaDependencyBackend =
              scope.props.flatMap {
                case (_, backend) =>
                  backend.addScalaDependency(scalaDependency)
              }

            addScalaDependencyLocal >> addScalaDependencyBackend
          }
      )
    }
  }

  private val component = ReactComponentB[(State, Backend)]("Scaladex Search")
    .initialState_P(SearchState.fromProps)
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
            s"https://scaladex.scala-lang.org/$organization/$repository/$artifact"

          li(selected)(
            a(`class` := "scaladex", href := scaladexLink, target := "_blank")(
              iconic.externalLink
            ),
            remove,
            span(handlers)(
              logo
                .map(
                  url =>
                    img(src := url + "&s=40",
                        common,
                        alt := s"$organization logo or avatar"))
                .getOrElse(
                  img(src := "/assets/public/placeholder.svg",
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
                       .updateVersion(project, scalaDependency))(
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
            searchState.scalaDependencies.toList.sorted.map {
              case (p, d) =>
                renderProject(
                  p,
                  d.artifact,
                  remove = iconic.x(
                    `class` := "remove",
                    onClick ==> scope.backend.removeScalaDependency(p, d)
                  ),
                  options = renderOptions(p, d)
                )
            }
          )
        }

        fieldset(`class` := "scaladex")(
          legend("Scala Libraries"),
          div(`class` := "search")(
            added,
            input.search(
              ref := searchInputRef,
              placeholder := "Search for 'cats'",
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
