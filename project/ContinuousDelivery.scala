import sbt._
import Keys._

object ContinuousDelivery {
  private lazy val deployWhenMaster = taskKey[Unit]("Deploy when merging to master")

  private def deployWhenMasterTask: Def.Initialize[Task[Unit]] = Def.task {
    val logger = streams.value.log
    if(!sys.env.get("DRONE_PULL_REQUEST").isDefined) {
      logger.info("** Not a PR **")
    } else {
      logger.info("** PR **")
    }
  }

  val settings = Seq(
    deployWhenMaster := deployWhenMasterTask.value,
    commands += Command.command("continuousDelivery") { state =>
      val logger = state.log

      List(
        "DRONE",
        "DRONE_REPO",
        "DRONE_REPO_OWNER",
        "DRONE_REPO_NAME",
        "DRONE_REPO_SCM",
        "DRONE_REPO_LINK",
        "DRONE_REPO_AVATAR",
        "DRONE_REPO_BRANCH",
        "DRONE_REPO_PRIVATE",
        "DRONE_REPO_TRUSTED",
        "DRONE_BUILD_NUMBER",
        "DRONE_BUILD_EVENT",
        "DRONE_BUILD_STATUS",
        "DRONE_BUILD_LINK",
        "DRONE_BUILD_CREATED",
        "DRONE_BUILD_STARTED",
        "DRONE_BUILD_FINISHED",
        "DRONE_PREV_BUILD_STATUS",
        "DRONE_PREV_BUILD_NUMBER",
        "DRONE_PREV_COMMIT_SHA",
        "DRONE_COMMIT_SHA",
        "DRONE_COMMIT_REF",
        "DRONE_COMMIT_BRANCH",
        "DRONE_COMMIT_LINK",
        "DRONE_COMMIT_MESSAGE",
        "DRONE_COMMIT_AUTHOR",
        "DRONE_COMMIT_AUTHOR_EMAIL",
        "DRONE_ARCH",
        "DRONE_REMOTE_URL",
        "DRONE_PULL_REQUEST",
        "DRONE_TAG"
      ).foreach(key =>
        logger.info(s"$key = ${sys.env.get(key)}")
      )

      val newState = Command.process(";test ;deployWhenMaster", state)

      newState
    }
  )
}
