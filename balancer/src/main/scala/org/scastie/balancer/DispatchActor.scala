package org.scastie.balancer

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.actor.Props
import akka.event
import akka.pattern.ask
import akka.remote.DisassociatedEvent
import akka.util.Timeout
import org.scastie.api._
import org.scastie.storage._
import org.scastie.storage.filesystem._
import org.scastie.storage.inmemory._
import org.scastie.storage.mongodb._
import org.scastie.storage.postgres.PostgresContainer
import org.scastie.util._
import com.typesafe.config.ConfigFactory

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.Executors
import scala.concurrent._
import scala.concurrent.duration._

case class Address(host: String, port: Int)
case class SbtConfig(config: String)

case class UserTrace(ip: String, user: Option[User])

case class InputsWithIpAndUser(inputs: BaseInputs, user: UserTrace)

case class RunSnippet(inputs: InputsWithIpAndUser)
case class SaveSnippet(inputs: InputsWithIpAndUser)
case class UpdateSnippet(snippetId: SnippetId, inputs: InputsWithIpAndUser)
case class DeleteSnippet(snippetId: SnippetId)
case class DownloadSnippet(snippetId: SnippetId)

case class ForkSnippet(snippetId: SnippetId, inputs: InputsWithIpAndUser)

case class FetchSnippet(snippetId: SnippetId)
case class FetchLatestSnippet(snippetId: SnippetId)
case class FetchOldSnippet(id: Int)
case class FetchUserSnippets(user: User)

case class ReceiveStatus(requester: ActorRef)

case class Run(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId)

case class Done(progress: SnippetProgress, retries: Int)

case object Ping

/**
  * This Actor creates and takes care of two dispatchers: SbtDispatcher and ScalaCliDispatcher.
  * It will receive every request and forward to the proper dispatcher every request.
  *
  * @param progressActor
  * @param statusActor
  */
class DispatchActor(progressActor: ActorRef, statusActor: ActorRef)
// extends PersistentActor with AtLeastOnceDelivery
    extends Actor
    with ActorLogging {

  private val config =
    ConfigFactory.load().getConfig("org.scastie.balancer")

  // Dispatchers
  val sbtDispatcher: ActorRef = context.actorOf(
    Props(new SbtDispatcher(config, progressActor, statusActor)),
    "SbtDispatcher"
  )

  val scliDispatcher: ActorRef = context.actorOf(
    Props(new ScalaCliDispatcher(config, progressActor, statusActor)),
    "ScalaCliDispatcher"
  )

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e =>
      log.error(e, "failure")
      SupervisorStrategy.resume
  }

  import context._

  system.scheduler.schedule(0.seconds, 30.seconds) {
    self ! Ping
  }

  override def preStart(): Unit = {
    statusActor ! SetDispatcher(self)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    container.close()
  }

  val containerType = config.getString("snippets-storage")

  private val container =
    containerType match {
      case "memory" => new InMemoryContainer()
      case "mongo"  => new MongoDBContainer()(ExecutionContext.fromExecutor(Executors.newWorkStealingPool()))
      case "mongo-local"  => new MongoDBContainer(defaultConfig = true)(ExecutionContext.fromExecutor(Executors.newWorkStealingPool()))
      case "postgres" => new PostgresContainer()(ExecutionContext.fromExecutor(Executors.newWorkStealingPool()))
      case "postgres-local" => new PostgresContainer(defaultConfig = true)(ExecutionContext.fromExecutor(Executors.newWorkStealingPool()))
      case "files" => new FilesystemContainer(
        Paths.get(config.getString("snippets-dir")),
        Paths.get(config.getString("old-snippets-dir"))
      )(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()))
      case _ =>
        println("fallback to in-memory container")
        new InMemoryContainer
    }

  def run(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId) =
    self ! Run(inputsWithIpAndUser, snippetId)

  private def logError[T](f: Future[T]) = {
    f.recover {
      case e => log.error(e, "failed future")
    }
  }

  def receive: Receive = event.LoggingReceive(event.Logging.InfoLevel) {
    case RunnerPong => ()

    case format: FormatRequest =>
      sbtDispatcher.tell(format, sender())

    case x @ RunSnippet(inputsWithIpAndUser) =>
      log.info(s"starting ${x}")
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
      val sender = this.sender()
      logError(container.create(inputs, user.map(u => UserLogin(u.login))).map { snippetId =>
        sender ! snippetId
        run(inputsWithIpAndUser, snippetId)
        log.info(s"finished ${x}")
      })

    case SaveSnippet(inputsWithIpAndUser) =>
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
      val sender = this.sender()
      logError(container.save(inputs, user.map(u => UserLogin(u.login))).map { snippetId =>
        sender ! snippetId
        run(inputsWithIpAndUser, snippetId)
      })

    case UpdateSnippet(snippetId, inputsWithIpAndUser) =>
      val sender = this.sender()
      logError(container.update(snippetId, inputsWithIpAndUser.inputs).map { updatedSnippetId =>
        sender ! updatedSnippetId
        updatedSnippetId.foreach(
          snippetIdU => run(inputsWithIpAndUser, snippetIdU)
        )
      })

    case ForkSnippet(snippetId, inputsWithIpAndUser) =>
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) =
        inputsWithIpAndUser
      val sender = this.sender()
      logError(
        container
          .fork(snippetId, inputs, user.map(u => UserLogin(u.login)))
          .map { forkedSnippetId =>
            sender ! Some(forkedSnippetId)
            run(inputsWithIpAndUser, forkedSnippetId)
          }
      )

    case DeleteSnippet(snippetId) =>
      val sender = this.sender()
      logError(container.deleteAll(snippetId).map(_ => sender ! (())))

    case DownloadSnippet(snippetId) =>
      val sender = this.sender()
      logError(container.downloadSnippet(snippetId).map(sender ! _))

    case FetchSnippet(snippetId) =>
      val sender = this.sender()
      logError(container.readSnippet(snippetId).map(sender ! _))

    // ! TODO
    case FetchLatestSnippet(snippetId) =>
      val sender = this.sender()
      logError(container.readLatestSnippet(snippetId).map(sender ! _))

    case FetchUserSnippets(user) =>
      val sender = this.sender()
      logError(container.listSnippets(UserLogin(user.login)).map(sender ! _))

    case FetchScalaJs(snippetId) =>
      val sender = this.sender()
      logError(container.readScalaJs(snippetId).map(sender ! _))

    case FetchScalaSource(snippetId) =>
      val sender = this.sender()
      logError(container.readScalaSource(snippetId).map(sender ! _))

    case FetchScalaJsSourceMap(snippetId) =>
      val sender = this.sender()
      logError(container.readScalaJsSourceMap(snippetId).map(sender ! _))

    case x @ ReceiveStatus(requester) =>
      sbtDispatcher.tell(x, sender())
      scliDispatcher.tell(x, sender())

    case statusProgress: StatusProgress =>
      statusActor ! statusProgress

    case progress: SnippetProgress =>
      val sender = this.sender()


      logError(
        container.appendOutput(progress)
          .recover {
            case e =>
              log.error(e, s"failed to save $progress from $sender")
              e
          }
          .map(sender ! _)
      )

    case run: Run => {
      run.inputsWithIpAndUser.inputs.target match {
        case _: ScalaCli =>
          log.info(s"Forwarding run to Scala-CLI dispatcher: ${run.snippetId}")
          scliDispatcher ! run
        case _ =>
          log.info(s"Forwarding run to SBT dispatcher: ${run.snippetId}")
          sbtDispatcher ! run
      }
    }

    case ping: Ping.type =>
      implicit val timeout: Timeout = Timeout(10.seconds)
      val seq = Future.sequence(
        List(scliDispatcher, sbtDispatcher).map {
          s => (s ? Ping).map(_ =>
              log.info(s"Pinged ${s}")
            ).recover(_ =>
              log.info(s"Failed to ping ${s}")
            )
        }
      )
  }


}
