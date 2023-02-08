package com.olegych.scastie.balancer

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.event
import akka.pattern.ask
import akka.remote.DisassociatedEvent
import akka.util.Timeout
import com.olegych.scastie.api
import com.olegych.scastie.api._
import com.olegych.scastie.storage._
import com.olegych.scastie.storage.filesystem._
import com.olegych.scastie.storage.inmemory._
import com.olegych.scastie.storage.mongodb._
import com.olegych.scastie.util._
import com.typesafe.config.ConfigFactory

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.Executors
import scala.concurrent._
import scala.concurrent.duration._

case class Address(host: String, port: Int)
case class SbtConfig(config: String)

case class UserTrace(ip: String, user: Option[User])

case class InputsWithIpAndUser(inputs: Inputs, user: UserTrace)

case class RunSnippet(inputs: InputsWithIpAndUser)
case class SaveSnippet(inputs: InputsWithIpAndUser)
case class UpdateSnippet(snippetId: SnippetId, inputs: InputsWithIpAndUser)
case class DeleteSnippet(snippetId: SnippetId)
case class DownloadSnippet(snippetId: SnippetId)

case class ForkSnippet(snippetId: SnippetId, inputs: InputsWithIpAndUser)

case class FetchSnippet(snippetId: SnippetId)
case class FetchOldSnippet(id: Int)
case class FetchUserSnippets(user: User)

case class ReceiveStatus(requester: ActorRef)

@deprecated("Scheduled for removal", "2023-04-30")
case class GetPrivacyPolicy(user: User)
@deprecated("Scheduled for removal", "2023-04-30")
case class SetPrivacyPolicy(user: User, status: Boolean)
@deprecated("Scheduled for removal", "2023-04-30")
case class RemovePrivacyPolicy(user: User)
@deprecated("Scheduled for removal", "2023-04-30")
case class RemoveAllUserSnippets(user: User)

case class Run(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId)

case class Done(progress: api.SnippetProgress, retries: Int)

case object Ping

class DispatchActor(progressActor: ActorRef, statusActor: ActorRef)
// extends PersistentActor with AtLeastOnceDelivery
    extends Actor
    with ActorLogging {

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e =>
      log.error(e, "failure")
      SupervisorStrategy.resume
  }

  private val config =
    ConfigFactory.load().getConfig("com.olegych.scastie.balancer")
  private val host = config.getString("remote-hostname")
  private val sbtPortsStart = config.getInt("remote-sbt-ports-start")
  private val sbtPortsSize = config.getInt("remote-sbt-ports-size")

  private val sbtPorts = (0 until sbtPortsSize).map(sbtPortsStart + _)

  private def connectRunner(
      runnerName: String,
      actorName: String,
      host: String
  )(port: Int): ((String, Int), ActorSelection) = {
    val path = s"akka://$runnerName@$host:$port/user/$actorName"
    log.info(s"Connecting to ${path}")
    val selection = context.actorSelection(path)
    selection ! ActorConnected
    (host, port) -> selection
  }

  private var remoteSbtSelections =
    sbtPorts.map(connectRunner("SbtRunner", "SbtActor", host)).toMap

  private var sbtLoadBalancer: SbtBalancer = {
    val sbtServers = remoteSbtSelections.to(Vector).map {
      case (_, ref) =>
        val state: SbtState = SbtState.Unknown
        Server(ref, Inputs.default, state)
    }

    LoadBalancer(servers = sbtServers)
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
      case "mongo-local"  => new MongoDBContainer(defaultConfig = false)(ExecutionContext.fromExecutor(Executors.newWorkStealingPool()))
      case "files" => new FilesystemContainer(
        Paths.get(config.getString("snippets-dir")),
        Paths.get(config.getString("old-snippets-dir"))
      )(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()))
      case _ =>
        println("fallback to in-memory container")
        new InMemoryContainer
    }

  private def updateSbtBalancer(newSbtBalancer: SbtBalancer): Unit = {
    if (sbtLoadBalancer != newSbtBalancer) {
      statusActor ! SbtLoadBalancerUpdate(newSbtBalancer)
    }
    sbtLoadBalancer = newSbtBalancer
    ()
  }

  //can be called from future
  private def run(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId): Unit = {
    self ! Run(inputsWithIpAndUser, snippetId)
  }
  //cannot be called from future
  private def run0(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId): Unit = {

    val InputsWithIpAndUser(inputs, UserTrace(ip, user)) = inputsWithIpAndUser

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, inputs)

    val task = Task(inputs, Ip(ip), TaskId(snippetId), Instant.now)

    sbtLoadBalancer.add(task) match {
      case Some((server, newBalancer)) =>
        updateSbtBalancer(newBalancer)

        server.ref.tell(
          SbtTask(snippetId, inputs, ip, user.map(_.login), progressActor),
          self
        )
      case _ => ()
    }
  }

  private def logError[T](f: Future[T]) = {
    f.recover {
      case e => log.error(e, "failed future")
    }
  }

  def receive: Receive = event.LoggingReceive(event.Logging.InfoLevel) {
    case SbtPong => ()

    case format: FormatRequest =>
      val server = sbtLoadBalancer.getRandomServer
      server.foreach(_.ref.tell(format, sender()))
      ()

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

    case FetchOldSnippet(id) =>
      val sender = this.sender()
      logError(container.readOldSnippet(id).map(sender ! _))

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
    case GetPrivacyPolicy(user) =>
      val sender = this.sender()
      logError(container.getPrivacyPolicyResponse(UserLogin(user.login)).map(sender ! _))
    case SetPrivacyPolicy(user, status) =>
      val sender = this.sender()
      logError(container.setPrivacyPolicyResponse(UserLogin(user.login), status).map(sender ! _))
    case RemovePrivacyPolicy(user) =>
      val sender = this.sender()
      logError(container.deleteUser(UserLogin(user.login)).map(sender ! _))
    case RemoveAllUserSnippets(user) =>
      val sender = this.sender()
      logError(container.removeUserSnippets(UserLogin(user.login)).map(sender ! _))

    case progress: api.SnippetProgress =>
      val sender = this.sender()
      if (progress.isDone) {
        self ! Done(progress, retries = 100)
      }
      logError(
        container
          .appendOutput(progress)
          .recover {
            case e =>
              log.error(e, s"failed to save $progress from $sender")
              e
          }
          .map(sender ! _)
      )

    case done: Done =>
      done.progress.snippetId.foreach { sid =>
        val newBalancer = sbtLoadBalancer.done(TaskId(sid))
        newBalancer match {
          case Some(newBalancer) =>
            updateSbtBalancer(newBalancer)
          case None =>
            if (done.retries >= 0) {
              system.scheduler.scheduleOnce(1.second) {
                self ! done.copy(retries = done.retries - 1)
              }
            } else {
              val taskIds =
                sbtLoadBalancer.servers.flatMap(_.mailbox.map(_.taskId))
              log.error(s"stopped retrying to update ${taskIds} with ${done}")
            }
        }
      }

    case event: DisassociatedEvent =>
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteSbtSelections.get((host, port))
      } {
        log.warning("removing disconnected: {}", ref)
        val previousRemoteSbtSelections = remoteSbtSelections
        remoteSbtSelections = remoteSbtSelections - ((host, port))
        if (previousRemoteSbtSelections != remoteSbtSelections) {
          updateSbtBalancer(sbtLoadBalancer.removeServer(ref))
        }
      }

    case SbtUp =>
      log.info("SbtUp")

    case Replay(SbtRun(snippetId, inputs, progressActor, snippetActor)) =>
      log.info("Replay: " + inputs.code)

    case SbtRunnerConnect(runnerHostname, runnerAkkaPort) =>
      if (!remoteSbtSelections.contains((runnerHostname, runnerAkkaPort))) {
        log.info("Connected Runner {}", runnerAkkaPort)

        val sel = connectRunner("SbtRunner", "SbtActor", runnerHostname)(
          runnerAkkaPort
        )
        val (_, ref) = sel

        remoteSbtSelections = remoteSbtSelections + sel

        val state: SbtState = SbtState.Unknown

        updateSbtBalancer(
          sbtLoadBalancer.addServer(
            Server(ref, Inputs.default, state)
          )
        )
      }

    case ReceiveStatus(requester) =>
      sender() ! LoadBalancerInfo(sbtLoadBalancer, requester)

    case statusProgress: StatusProgress =>
      statusActor ! statusProgress

    case run: Run =>
      run0(run.inputsWithIpAndUser, run.snippetId)
    case ping: Ping.type =>
      implicit val timeout: Timeout = Timeout(10.seconds)
      logError(Future.sequence {
        sbtLoadBalancer.servers.map { s =>
          (s.ref ? SbtPing)
            .map { _ =>
              log.info(s"pinged ${s.ref} server")
            }
            .recover {
              case e => log.error(e, s"couldn't ping ${s} server")
            }
        }
      })
  }
}
