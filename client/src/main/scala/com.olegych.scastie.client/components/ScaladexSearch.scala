package com.olegych.scastie.client.components

import com.olegych.scastie.api._

import play.api.libs.json.Json

import japgolly.scalajs.react._, vdom.all._, extra._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope

import org.scalajs.dom
import dom.ext.KeyCode
import dom.raw.{HTMLInputElement, HTMLElement}
import dom.ext.Ajax

import scalajs.concurrent.JSExecutionContext.Implicits.queue

final case class ScaladexSearch(
    removeScalaDependency: ScalaDependency ~=> Callback,
    updateDependencyVersion: (ScalaDependency, String) ~=> Callback,
    addScalaDependency: (ScalaDependency, Project) ~=> Callback,
    librariesFrom: Map[ScalaDependency, Project],
    scalaTarget: ScalaTarget
) {
  @inline def render: VdomElement = ScaladexSearch.component(this)
}

object ScaladexSearch {

  implicit val propsReusability: Reusability[ScaladexSearch] =
    Reusability.caseClass[ScaladexSearch]

  implicit val selectedReusability: Reusability[Selected] =
    Reusability.caseClass[Selected]

  implicit val stateReusability: Reusability[SearchState] =
    Reusability.caseClass[SearchState]

  private[ScaladexSearch] object SearchState {
    def default = SearchState(
      query = "",
      selectedIndex = 0,
      projects = List.empty,
      selecteds = List.empty
    )

    def fromProps(props: ScaladexSearch): SearchState = {
      SearchState.default.copy(
        selecteds = props.librariesFrom.toList.map {
          case (scalaDependency, project) =>
            Selected(
              project,
              scalaDependency,
              ReleaseOptions(
                scalaDependency.groupId,
                List(scalaDependency.version),
                scalaDependency.version
              )
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

    val search: List[(Project, String)] =
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

  private implicit val projectOrdering: Ordering[Project] =
    Ordering.by { project: Project =>
      (project.organization, project.repository)
    }

  private implicit val scalaDependenciesOrdering: Ordering[ScalaDependency] =
    Ordering.by { scalaDependency: ScalaDependency =>
      scalaDependency.artifact
    }

  private implicit val selectedOrdering: Ordering[Selected] =
    Ordering.by { selected: Selected =>
      (selected.project, selected.release)
    }

  private var projectListRef: HTMLElement = _
  private var searchInputRef: HTMLInputElement = _

  private[ScaladexSearch] class ScaladexSearchBackend(
      scope: BackendScope[ScaladexSearch, SearchState]
  ) {
    def keyDown(e: ReactKeyboardEventFromInput): Callback = {

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

        def scrollToSelected(selected: Int, total: Int) = {
          projectListRef.scrollTop = Math.abs(
            interpolate(projectListRef.scrollHeight, total, selected + diff)
          )
        }

        def selectProject =
          scope.modState(
            s =>
              s.copy(
                selectedIndex = clamp(s.search.size, s.selectedIndex + diff)
            )
          )

        def scrollToSelectedProject =
          scope.state.map(
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
                addArtifact(s.search(s.selectedIndex))
              else
                Callback.empty
          )

        addArtifactIfInRange >> Callback(searchInputRef.focus)
      } else {
        Callback.empty
      }
    }

    def addArtifact(projectAndArtifact: (Project, String)): Callback = {
      val (project, artifact) = projectAndArtifact
      scope.props.flatMap(
        props => fetchReleaseOptions(project, artifact, props.scalaTarget)
      )
    }

    def removeSelected(selected: Selected): Callback = {

      def removeDependencyLocal =
        scope.modState(_.removeSelected(selected))

      def removeDependecyBackend =
        scope.props.flatMap(_.removeScalaDependency(selected.release))

      removeDependencyLocal >> removeDependecyBackend
    }

    def updateVersion(selected: Selected)(e: ReactEventFromInput): Callback = {
      e.extract(_.target.value) { version =>
        def updateDependencyVersionLocal =
          scope.modState(_.updateVersion(selected, version))

        def updateDependencyVersionBackend =
          scope.props.flatMap(
            _.updateDependencyVersion(selected.release, version)
          )

        updateDependencyVersionLocal >> updateDependencyVersionBackend
      }
    }

    def selectIndex(index: Int): Callback =
      scope.modState(s => s.copy(selectedIndex = index))

    def resetQuery(): Callback =
      scope.modState(s => s.copy(query = "", projects = Nil))

    def setQuery(e: ReactEventFromInput): Callback = {
      e.extract(_.target.value) { value =>
        scope.modState(_.copy(query = value, selectedIndex = 0),
                       fetchProjects())
      }
    }

    private def fetchProjects(): Callback = {
      def fetch(target: ScalaTarget, searchState: SearchState): Callback = {
        if (!searchState.query.isEmpty) {
          val query = toQuery(
            Map("q" -> searchState.query) ++ target.scaladexRequest
          )

          Callback.future(
            Ajax
              .get(scaladexApiUrl + "/search" + query)
              .map { ret =>
                Json
                  .fromJson[List[Project]](Json.parse(ret.responseText))
                  .asOpt
                  .getOrElse(Nil)
              }
              .map(projects => scope.modState(_.setProjects(projects)))
          )
        } else scope.modState(_.clearProjects)
      }

      for {
        props <- scope.props
        searchState <- scope.state
        _ <- fetch(props.scalaTarget, searchState)
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
          .map(
            ret =>
              Json.fromJson[ReleaseOptions](Json.parse(ret.responseText)).asOpt
          )
          .map {
            case Some(options) => {
              val scalaDependency =
                ScalaDependency(
                  options.groupId,
                  artifact,
                  target,
                  options.version
                )

              def addScalaDependencyLocal =
                scope
                  .modState(_.addDependency(project, scalaDependency, options))

              def addScalaDependencyBackend =
                scope.props
                  .flatMap(_.addScalaDependency(scalaDependency, project))

              addScalaDependencyLocal >> addScalaDependencyBackend
            }
            case None => Callback(())
          }
      )
    }
  }

  private def render(
      scope: RenderScope[ScaladexSearch, SearchState, ScaladexSearchBackend],
      props: ScaladexSearch,
      searchState: SearchState
  ): VdomElement = {
    def selectedIndex(index: Int, selected: Int) =
      (cls := "selected").when(index == selected)

    def renderProject(project: Project,
                      artifact: String,
                      selected: TagMod,
                      handlers: TagMod = EmptyVdom,
                      remove: TagMod = EmptyVdom,
                      options: TagMod = EmptyVdom) = {
      import project._

      val common = TagMod(title := organization, cls := "logo")
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

      div(cls := "result", selected, handlers)(
        a(cls := "scaladexresult", href := scaladexLink, target := "_blank")(
          i(cls := "fa fa-external-link")
        ),
        remove,
        span(
          logo
            .flatMap(
              _.map(
                url =>
                  img(src := url + "&s=40",
                      common,
                      alt := s"$organization logo or avatar")
              ).headOption
            )
            .getOrElse(
              img(src := "/assets/public/placeholder.svg",
                  common,
                  alt := s"placeholder for $organization")
            ),
          span(cls := "artifact")(label)
        ),
        options
      )
    }

    def renderOptions(selected: Selected) = {
      div(cls := "select-wrapper")(
        select(value := selected.release.version,
               onChange ==> scope.backend.updateVersion(selected))(
          selected.options.versions.reverse
            .map(v => option(value := v)(v))
            .toTagMod
        )
      )
    }

    val added = {
      val hideAdded =
        if (searchState.selecteds.isEmpty) display.none
        else EmptyVdom

      div(cls := "added", hideAdded)(
        searchState.selecteds.sorted
          .map(
            selected =>
              renderProject(
                selected.project,
                selected.release.artifact,
                i(cls := "fa fa-close")(
                  onClick --> scope.backend.removeSelected(selected),
                  cls := "remove"
                ),
                options = renderOptions(selected)
            )
          )
          .toTagMod
      )
    }

    val displayResults =
      if (searchState.search.isEmpty) display.none
      else display.block

    val displayClose =
      if (searchState.search.isEmpty) display.none
      else display.`inline-block`

    div(cls := "search", cls := "library")(
      added,
      div(cls := "search-input")(
        input.search.ref(searchInputRef = _)(
          cls := "search-query",
          placeholder := "Search for 'cats'",
          value := searchState.query,
          onChange ==> scope.backend.setQuery,
          onKeyDown ==> scope.backend.keyDown
        ),
        div(cls := "close", displayClose)(
          i(cls := "fa fa-close")(
            onClick --> scope.backend.resetQuery
          )
        )
      ),
      div.ref(projectListRef = _)(cls := "results", displayResults)(
        searchState.search.zipWithIndex.map {
          case ((project, artifact), index) =>
            renderProject(
              project,
              artifact,
              selected = selectedIndex(index, searchState.selectedIndex),
              handlers = TagMod(
                onClick --> scope.backend
                  .addArtifact((project, artifact)),
                onMouseOver --> scope.backend.selectIndex(index)
              )
            )
        }.toTagMod
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[ScaladexSearch]("Scaladex Search")
      .initialStateFromProps(SearchState.fromProps)
      .backend(new ScaladexSearchBackend(_))
      .renderPS(render)
      .componentDidMount(_ => Callback(searchInputRef.focus))
      .configure(Reusability.shouldComponentUpdate)
      .build
}
