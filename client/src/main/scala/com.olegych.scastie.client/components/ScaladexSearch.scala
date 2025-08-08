package com.olegych.scastie.client.components

import com.olegych.scastie.api.ScalaTarget.Jvm
import com.olegych.scastie.api.ScalaTarget.Scala3
import com.olegych.scastie.api._
import com.olegych.scastie.buildinfo.BuildInfo
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope
import org.scalajs.dom
import play.api.libs.json.Json

import scala.concurrent.Future

import vdom.all._
import dom.ext.KeyCode
import dom.{HTMLInputElement, HTMLElement}
import scalajs.js.Thenable.Implicits._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import com.olegych.scastie.client.i18n.I18n

final case class ScaladexSearch(
    removeScalaDependency: ScalaDependency ~=> Callback,
    updateDependencyVersion: (ScalaDependency, String) ~=> Callback,
    addScalaDependency: (ScalaDependency, Project) ~=> Callback,
    librariesFrom: Map[ScalaDependency, Project],
    scalaTarget: ScalaTarget,
    isDarkTheme: Boolean,
    language: String
) {
  @inline def render: VdomElement = ScaladexSearch.component(this)
}

object ScaladexSearch {

  implicit val propsReusability: Reusability[ScaladexSearch] =
    Reusability.derive[ScaladexSearch]

  implicit val selectedReusability: Reusability[Selected] =
    Reusability.derive[Selected]

  implicit val stateReusability: Reusability[SearchState] =
    Reusability.derive[SearchState]

  private[ScaladexSearch] object SearchState {
    def default: SearchState = SearchState(
      query = "",
      selectedIndex = 0,
      projects = List.empty,
      selecteds = List.empty
    )
  }

  private[ScaladexSearch] case class Selected(
      project: Project,
      release: ScalaDependency,
      options: ReleaseOptions
  ) {
    def matches(p: Project, artifact: String) = p == project && release.artifact == artifact
  }

  private[ScaladexSearch] case class SearchState(
      query: String,
      selectedIndex: Int,
      projects: List[(Project, ScalaTarget)],
      selecteds: List[Selected]
  ) {

    private val selectedProjectsArtifacts = selecteds
      .map(selected => (selected.project, selected.release.artifact, None, selected.release.target))
      .toSet

    private def matchScore(query: String, artifact: String, project: Project): Int = {
      val queryLower = query.toLowerCase
      val artifactLower = artifact.toLowerCase
      val projectLower = project.repository.toLowerCase
      val orgLower = project.organization.toLowerCase

      (queryLower, artifactLower, projectLower, orgLower) match {
        case (q, a, _, _) if a == q => 1000
        case (q, a, _, _) if a.startsWith(q) => 800
        case (q, a, _, _) if a.contains(q) => 600
        case (q, _, p, _) if p.contains(q) => 400
        case (q, _, _, o) if o.contains(q) => 200
        case _ => 0
      }
    }

    val search: List[(Project, String, Option[String], ScalaTarget)] = {
      val results = projects
        .flatMap {
          case (project, target) => project.artifacts.map(artifact => (project, artifact, None, target))
        }
        .filter { projectAndArtifact =>
          !selectedProjectsArtifacts.contains(projectAndArtifact)
        }

      if (query.nonEmpty) {
        results.sortBy({ case (project, artifact, _, _) =>
          -matchScore(query, artifact, project)
        })(Ordering[Int])
      } else {
        results.sortBy { case (project, artifact, _, _) =>
          (project.organization, project.repository, artifact)
        }
      }
    }
    def removeSelected(selected: Selected): SearchState = {
      copy(selecteds = selecteds.filterNot(_.release.matches(selected.release)))
    }

    def addSelected(selected: Selected): SearchState = {
      copy(
        selecteds = selected :: selecteds.filterNot(_.release.matches(selected.release))
      )
    }

    def updateVersion(selected: Selected, version: String): SearchState = {
      val updated = selected.copy(release = selected.release.copy(version = version), options = selected.options.copy(version = version))
      copy(
        selecteds = selecteds.filterNot(_.release.matches(updated.release)) :+ updated
      )
    }

    def setProjects(projects: List[(Project, ScalaTarget)]): SearchState = {
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

  private val projectListRef = Ref[HTMLElement]
  private val searchInputRef = Ref[HTMLInputElement]

  private[ScaladexSearch] class ScaladexSearchBackend(scope: BackendScope[ScaladexSearch, SearchState]) {
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
          projectListRef.unsafeGet().scrollTop = Math.abs(
            interpolate(projectListRef.unsafeGet().scrollHeight, total, selected + diff)
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
          for {
            state <- scope.state
            props <- scope.props
            _ <- if (0 <= state.selectedIndex && state.selectedIndex < state.search.size) {
              val (p, a, v, t) = state.search(state.selectedIndex)
              addArtifact((p, a, v), t, state)
            } else Callback.empty
          } yield ()

        addArtifactIfInRange >> Callback(searchInputRef.unsafeGet().focus())
      } else {
        Callback.empty
      }
    }

    def addArtifact(projectAndArtifact: (Project, String, Option[String]),
                    target: ScalaTarget,
                    state: SearchState,
                    localOnly: Boolean = false): Callback = {
      val (project, artifact, version) = projectAndArtifact
      if (state.selecteds.exists(_.matches(project, artifact))) Callback(())
      else
        Callback.future {
          fetchSelected(project, artifact, target, version).map {
            case Some(selected) if !state.selecteds.exists(_.release.matches(selected.release)) =>
              def addScalaDependencyLocal =
                scope.modState(_.addSelected(selected))

              def addScalaDependencyBackend =
                if (localOnly) Callback(()) else scope.props.flatMap(_.addScalaDependency((selected.release, selected.project)))

              addScalaDependencyBackend >> addScalaDependencyLocal
            case _ => Callback(())
          }
        }
    }

    def removeSelected(selected: Selected): Callback = {

      def removeDependencyLocal =
        scope.modState(_.removeSelected(selected))

      def removeDependecyBackend =
        scope.props.flatMap(_.removeScalaDependency(selected.release))

      removeDependecyBackend >> removeDependencyLocal
    }

    def updateVersion(selected: Selected)(e: ReactEventFromInput): Callback = {
      val version = e.target.value
      def updateDependencyVersionLocal =
        scope.modState(_.updateVersion(selected, version))

      def updateDependencyVersionBackend =
        scope.props.flatMap(
          _.updateDependencyVersion((selected.release, version))
        )

      updateDependencyVersionBackend >> updateDependencyVersionLocal
    }

    def selectIndex(index: Int): Callback =
      scope.modState(s => s.copy(selectedIndex = index))

    def resetQuery: Callback =
      scope.modState(s => s.copy(query = "", projects = Nil))

    def setQuery(e: ReactEventFromInput): Callback = {
      e.extract(_.target.value) { value =>
        scope.modState(_.copy(query = value, selectedIndex = 0), fetchProjects())
      }
    }

    private def fetchProjects(): Callback = {
      def fetch(target: ScalaTarget, searchState: SearchState): Callback = {
        if (!searchState.query.isEmpty) {

          def queryAndParse(t: ScalaTarget): Future[List[(Project, ScalaTarget)]] = {
            val q = toQuery(t.scaladexRequest + ("q" -> searchState.query))
            for {
              response <- dom.fetch(scaladexApiUrl + "/search" + q)
              text <- response.text()
            } yield {
              Json.fromJson[List[Project]](Json.parse(text)).asOpt.getOrElse(Nil).map(_ -> t)
            }
          }

          val projsForThisTarget = queryAndParse(target)
          val projects: Future[List[(Project, ScalaTarget)]] = target match {
            // If scala3 but no scala 3 versions available, offer 2.13 artifacts
            case Scala3(_) =>
              projsForThisTarget.flatMap { ls =>
                queryAndParse(Jvm(BuildInfo.latest213))
                  .map(arts213 => ls ::: arts213)
              }
            case _ => projsForThisTarget
          }

          Callback.future(
            projects.map(projects => scope.modState(_.setProjects(projects)))
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

    private def fetchSelected(project: Project, artifact: String, target: ScalaTarget, version: Option[String]) = {
      val query = toQuery(
        Map(
          "organization" -> project.organization,
          "repository" -> project.repository
        ) ++ target.scaladexRequest
      )

      for {
        response <- dom.fetch(scaladexApiUrl + "/project" + query)
        text <- response.text()
        
        artifactResponse <- dom.fetch(scaladexApiUrl + s"/v1/projects/${project.organization}/${project.repository}/versions/latest")
        artifactText <- artifactResponse.text()
        artifactJson = Json.parse(artifactText)
        
        matchingArtifact = artifactJson.as[List[play.api.libs.json.JsObject]].find { artifactObj =>
          val artifactId = (artifactObj \ "artifactId").asOpt[String].getOrElse("")
          val targetSuffix = target.targetType match {
            case ScalaTargetType.Scala3 => "_3"
            case ScalaTargetType.Scala2 => s"_${target.binaryScalaVersion}"
            case ScalaTargetType.JS => s"_sjs1_${target.binaryScalaVersion}"
          }
          artifactId == artifact || artifactId == s"${artifact}${targetSuffix}"
        }
        matchingGroupId = matchingArtifact.flatMap(obj => (obj \ "groupId").asOpt[String]).getOrElse("")
        matchingVersion = matchingArtifact.flatMap(obj => (obj \ "version").asOpt[String]).orElse(version)
      } yield {
        Json.fromJson[ReleaseOptions](Json.parse(text)).asOpt.map { options =>
          {
            Selected(
              project = project,
              release = ScalaDependency(
                groupId = if (matchingGroupId.nonEmpty) matchingGroupId else options.groupId,
                artifact = artifact,
                target = target,
                version = matchingVersion.getOrElse(options.version),
              ),
              options = options,
            )
          }
        }
      }
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
                      scalaTarget: ScalaTarget,
                      selected: TagMod,
                      handlers: TagMod = EmptyVdom,
                      remove: TagMod = EmptyVdom,
                      options: TagMod = EmptyVdom) = {
      import project._

      val common = TagMod(title := organization, cls := "logo")
      val artifact2 =
        artifact
          .replace(project.repository + "-", "")
          .replace(project.repository, "")

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
            .map(url => img(src := (url + "&s=40"), common, alt := s"$organization logo or avatar"))
            .getOrElse(
              img(src := Assets.placeholder, common, alt := s"placeholder logo for $organization")
            ),
          span(cls := "artifact")(label),
          options,
          if (scalaTarget.binaryScalaVersion != props.scalaTarget.binaryScalaVersion)
            span(cls := "artifact")(s"(Scala ${scalaTarget.binaryScalaVersion} artifacts)")
          else ""
        ),
      )
    }

    def renderOptions(selected: Selected) = {
      div(cls := "select-wrapper")(
        select(
          selected.options.versions.reverse.map(v => option(value := v)(v)).toTagMod,
          value := selected.release.version,
          onChange ==> scope.backend.updateVersion(selected),
        )
      )
    }

    val added = {
      val hideAdded =
        if (searchState.selecteds.isEmpty) display.none
        else EmptyVdom

      div(cls := "added", hideAdded)(
        searchState.selecteds.sorted.map { selected =>
          renderProject(
            selected.project,
            selected.release.artifact,
            selected.release.target,
            i(cls := "fa fa-close")(
              onClick --> scope.backend.removeSelected(selected),
              cls := "remove"
            ),
            options = renderOptions(selected)
          )
        }.toTagMod
      )
    }

    val displayResults =
      if (searchState.search.isEmpty) display.none
      else display.block

    val displayClose =
      if (searchState.search.isEmpty) display.none
      else display.inlineBlock

    val toolkitEnabled = props.librariesFrom.keys.exists { dep =>
      dep.groupId == "org.scala-lang" &&
      dep.artifact == "toolkit" &&
      dep.target == props.scalaTarget
    }

    def handleToolkitToggle(enabled: Boolean): Callback = {
      val toolkitProject = Project(
        organization = "scala",
        repository = "toolkit",
        logo = Some("https://avatars.githubusercontent.com/u/57059?v=4"),
        artifacts = List("toolkit", "toolkit-test")
      )
      val artifact = "toolkit"
      val versionOpt: Option[String] = None

      if (enabled)
        scope.backend.addArtifact((toolkitProject, artifact, versionOpt), props.scalaTarget, searchState)
      else {
        searchState.selecteds.find { selected =>
          selected.release.groupId == "org.scala-lang" &&
          selected.release.artifact == "toolkit" &&
          selected.release.target == props.scalaTarget
        }.map(scope.backend.removeSelected(_)).getOrElse(Callback.empty)
      }
    }

    val toolkitSwitchElem = toolkitSwitch(
      isEnabled = toolkitEnabled,
      onToggle = handleToolkitToggle,
      isDarkTheme = props.isDarkTheme
    )

    div(cls := "search", cls := "library")(
      toolkitSwitchElem,
      added,
      div(cls := "search-input")(
        input.search.withRef(searchInputRef)(
          cls := "search-query",
          placeholder := I18n.t("Search for 'cats'"),
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
      div.withRef(projectListRef)(cls := "results", displayResults)(
        searchState.search.zipWithIndex.map {
          case ((project, artifact, version, target), index) =>
            renderProject(
              project,
              artifact,
              target,
              selected = selectedIndex(index, searchState.selectedIndex),
              handlers = TagMod(
                onClick --> scope.backend.addArtifact((project, artifact, version), target, scope.state),
                onMouseOver --> scope.backend.selectIndex(index)
              )
            )
        }.toTagMod
      ),
    )
  }

  private def toolkitSwitch(
    isEnabled: Boolean,
    onToggle: Boolean => Callback,
    isDarkTheme: Boolean
  ): VdomElement = {
    val switchId = s"switch-$label".replace(" ", "-")
    val sliderClass =
    if (isDarkTheme) "switch-slider dark" else "switch-slider"
    div(
      cls := "toolkit-switch",
    )(
      div(cls := "switch")(
        input(
          `type` := "checkbox",
          cls := "switch-input",
          id := switchId,
          checked := isEnabled,
          onChange ==> { (e: ReactEventFromInput) =>
            onToggle(e.target.checked)
          }
        ),
        label(
          cls := sliderClass,
          htmlFor := switchId
        )
      ),
      span(
        cls := "switch-description",
      )(I18n.t("Enable Toolkit"))
    )
  }

  private val component =
    ScalaComponent
      .builder[ScaladexSearch]("Scaladex Search")
      .initialState(SearchState.default)
      .backend(new ScaladexSearchBackend(_))
      .renderPS(render)
      .componentWillReceiveProps { x =>
        Callback.traverse(x.nextProps.librariesFrom.toList.sortBy(_._1.artifact)) { lib =>
          x.backend.addArtifact((lib._2, lib._1.artifact, Some(lib._1.version)), lib._1.target, x.state, localOnly = true)
        }
      }
      .configure(Reusability.shouldComponentUpdate)
      .build
}
