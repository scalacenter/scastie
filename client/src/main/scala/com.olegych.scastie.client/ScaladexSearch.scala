package com.olegych.scastie
package client

import api._

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
      selectedIndex = 0,
      projects = List(),
      selecteds = List()
    )

    def fromProps(props: (AppState, AppBackend)): SearchState = {
      val (state, _) = props

      SearchState.default.copy(
        selecteds = state.inputs.librariesFrom.toList.map {
          case (scalaDependency, project) =>
            Selected(
              project,
              scalaDependency,
              ReleaseOptions(scalaDependency.groupId,
                             List(scalaDependency.version))
            )
        }
      )
    }
  }

  private[ScaladexSearch] case class Selected(
      project: Project,
      release: ScalaDependency,
      options: ReleaseOptions
  )

  private[ScaladexSearch] case class SearchState(
      query: String,
      selectedIndex: Int,
      projects: List[Project],
      selecteds: List[Selected]
  ) {

    private val selectedProjectsArtifacts = selecteds
      .map(selected => (selected.project, selected.release.artifact))
      .toSet

    val search =
      projects
        .flatMap(
          project => project.artifacts.map(artifact => (project, artifact))
        )
        .filter(
          projectAndArtifact =>
            !selectedProjectsArtifacts.contains(projectAndArtifact)
        )

    def removeSelected(selected: Selected): SearchState = {
      copy(selecteds = selecteds.filterNot(_ == selected))
    }

    def addDependency(project: Project,
                      scalaDependency: ScalaDependency,
                      options: ReleaseOptions): SearchState = {
      copy(
        selecteds = Selected(project, scalaDependency, options) :: selecteds
      )
    }

    def updateVersion(selected: Selected, version: String): SearchState = {
      val updatedRelease = selected.release.copy(version = version)
      copy(
        selecteds = selecteds.map(
          s =>
            if (s == selected) s.copy(release = updatedRelease)
            else s
        )
      )
    }

    def setProjects(projects: List[Project]): SearchState = {
      copy(projects = projects)
    }

    def clearProjects: SearchState = {
      copy(projects = List())
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

  private implicit val selectedOrdering =
    Ordering.by { selected: Selected =>
      (selected.project, selected.release)
    }

  private val projectListRef = Ref[HTMLElement]("projectListRef")
  private val searchInputRef = Ref[HTMLInputElement]("searchInputRef")

  implicit final class ReactExt_DomNodeO[O[_], N <: dom.raw.Node](
      o: O[N]
  )(implicit O: OptionLike[O]) {

    def tryTo(f: HTMLElement => Unit) =
      Callback(O.toOption(o).flatMap(_.domToHtml).foreach(f))

    def tryFocus: Callback = tryTo(_.focus())
  }

  private[ScaladexSearch] class SearchBackend(
      scope: BackendScope[(AppState, AppBackend), SearchState]
  ) {
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
                Math.abs(interpolate(el.scrollHeight, total, selected + diff))
          )

        def selectProject =
          scope.modState(
            s =>
              s.copy(
                selectedIndex = clamp(s.search.size, s.selectedIndex + diff)
            )
          )

        def scrollToSelectedProject =
          scope.state.flatMap(
            s => scrollToSelected(s.selectedIndex, s.search.size)
          )

        selectProject >>
          e.preventDefaultCB >>
          scrollToSelectedProject

      } else if (e.keyCode == KeyCode.Enter) {

        def addArtifactIfInRange =
          scope.state.flatMap(
            s =>
              if (0 <= s.selectedIndex && s.selectedIndex < s.search.size)
                addArtifact(s.search.toList(s.selectedIndex))
              else
                Callback(())
          )

        addArtifactIfInRange >> searchInputRef(scope).tryFocus
      } else {
        Callback(())
      }
    }

    def addArtifact(
        projectAndArtifact: (Project, String)
    )(e: ReactEventI): Callback =
      addArtifact(projectAndArtifact)

    private def withBackend(f: AppBackend => Callback): Callback = {
      scope.props.flatMap {
        case (_, backend) =>
          f(backend)
      }
    }

    private def addArtifact(projectAndArtifact: (Project, String)): Callback = {
      val (project, artifact) = projectAndArtifact
      scope.props.flatMap {
        case (appState, _) =>
          fetchReleaseOptions(project, artifact, appState.inputs.target)
      }
    }

    def removeSelected(selected: Selected)(e: ReactEventI): Callback = {

      def removeDependencyLocal =
        scope.modState(_.removeSelected(selected))

      def removeDependecyBackend =
        withBackend(_.removeScalaDependency(selected.release))

      removeDependencyLocal >> removeDependecyBackend
    }

    def reloadSelecteds(
        librariesFrom: Map[ScalaDependency, Project]
    ): Callback = {
      scope.modState(
        s =>
          s.copy(
            selecteds = librariesFrom.toList.map {
              case (scalaDependency, project) =>
                Selected(
                  project,
                  scalaDependency,
                  ReleaseOptions(scalaDependency.groupId,
                                 List(scalaDependency.version))
                )
            }
        )
      )
    }

    def updateVersion(selected: Selected)(e: ReactEventI): Callback = {
      e.extract(_.target.value) { version =>
        def updateDependencyVersionLocal =
          scope.modState(_.updateVersion(selected, version))

        def updateDependencyVersionBackend =
          withBackend(_.updateDependencyVersion(selected.release, version))

        updateDependencyVersionLocal >> updateDependencyVersionBackend
      }
    }

    def selectIndex(index: Int)(e: ReactEventI): Callback = {
      scope.modState(s => s.copy(selectedIndex = index))
    }

    def resetQuery(e: ReactEventI): Callback = resetQuery()
    def resetQuery(): Callback =
      scope.modState(s => s.copy(query = "", projects = Nil))

    def setQuery(e: ReactEventI): Callback = {
      e.extract(_.target.value) { value =>
        scope.modState(_.copy(query = value, selectedIndex = 0),
                       fetchProjects())
      }
    }

    private def fetchProjects(): Callback = {
      def fetch(appState: AppState, searchState: SearchState): Callback = {
        if (!searchState.query.isEmpty) {
          val query = toQuery(
            Map("q" -> searchState.query) ++
              appState.inputs.target.scaladexRequest
          )

          Callback.future(
            Ajax
              .get(scaladexApiUrl + "/search" + query)
              .map(ret => uread[List[Project]](ret.responseText))
              .map(projects => scope.modState(_.setProjects(projects)))
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
              scope
                .modState(_.addDependency(project, scalaDependency, options))

            def addScalaDependencyBackend =
              withBackend(_.addScalaDependency(scalaDependency, project))

            addScalaDependencyLocal >> addScalaDependencyBackend
          }
      )
    }
  }

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("Scaladex Search")
      .initialState_P(SearchState.fromProps)
      .backend(new SearchBackend(_))
      .renderPS {
        case (scope, (state, backend), searchState) => {
          def selectedIndex(index: Int, selected: Int) = {
            if (index == selected) TagMod(`class` := "selected")
            else EmptyVdom
          }

          def renderProject(project: Project,
                            artifact: String,
                            selected: TagMod = EmptyVdom,
                            handlers: TagMod = EmptyVdom,
                            remove: TagMod = EmptyVdom,
                            options: TagMod = EmptyVdom) = {
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

            div(`class` := "result", selected, handlers)(
              a(`class` := "scaladexresult",
                href := scaladexLink,
                target := "_blank")(
                i(`class` := "fa fa-external-link")
              ),
              remove,
              span(
                logo
                  .map(
                    url =>
                      img(src := url + "&s=40",
                          common,
                          alt := s"$organization logo or avatar")
                  )
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

          def renderOptions(selected: Selected) = {
            div(`class` := "select-wrapper")(
              select(value := selected.release.version,
                     onChange ==> scope.backend.updateVersion(selected))(
                selected.options.versions.reverse
                  .map(v => option(value := v)(v)).toTagMod
              )
            )
          }

          val added = {
            val hideAdded =
              if (searchState.selecteds.isEmpty) display.none
              else EmptyVdom

            div(`class` := "added", hideAdded)(
              searchState.selecteds.toList.sorted.map(
                selected =>
                  renderProject(
                    selected.project,
                    selected.release.artifact,
                    i(`class` := "fa fa-close")(
                      onClick ==> scope.backend.removeSelected(selected),
                      `class` := "remove"
                    ),
                    options = renderOptions(selected)
                )
              ).toTagMod
            )
          }

          val displayResults =
            if (searchState.search.isEmpty) display.none
            else display.block

          val displayClose =
            if (searchState.search.isEmpty) display.none
            else display.`inline-block`

          div(`class` := "search", `id` := "library")(
            added,
            div(`class` := "search-input")(
              input.search(
                `class` := "search-query",
                ref := searchInputRef,
                placeholder := "Search for 'cats'",
                value := searchState.query,
                onChange ==> scope.backend.setQuery,
                onKeyDown ==> scope.backend.keyDown
              ),
              div(`class` := "close", displayClose)(
                i(`class` := "fa fa-close")(
                  onClick ==> scope.backend.resetQuery
                )
              )
            ),
            div(`class` := "results", ref := projectListRef, displayResults)(
              searchState.search.zipWithIndex.toList.map {
                case ((project, artifact), index) => {
                  renderProject(
                    project,
                    artifact,
                    selected = selectedIndex(index, searchState.selectedIndex),
                    handlers = TagMod(
                      onClick ==> scope.backend
                        .addArtifact((project, artifact)),
                      onMouseOver ==> scope.backend.selectIndex(index)
                    )
                  )
                }
              }
            )
          )
        }
      }
      .componentWillReceiveProps { v =>
        val (current, _) = v.currentProps
        val (next, _) = v.nextProps

        val reloadLibraries =
          if (next != current)
            v.$.backend.reloadSelecteds(next.inputs.librariesFrom)
          else Callback(())

        val resetQuery =
          if (next.inputs.copy(code = "") == Inputs.default.copy(code = ""))
            v.$.backend.resetQuery()
          else Callback(())

        reloadLibraries >> resetQuery
      }
      .componentDidMount(s => searchInputRef(s).tryFocus)
      .build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
