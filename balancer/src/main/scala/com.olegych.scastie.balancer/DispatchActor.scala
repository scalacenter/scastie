package com.olegych.scastie.balancer

import com.olegych.scastie.api
import com.olegych.scastie.api._
import com.olegych.scastie.util._
import com.olegych.scastie.storage._
import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSelection,
  OneForOneStrategy,
  SupervisorStrategy
}
import akka.remote.DisassociatedEvent
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.nio.file._

import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.{Executors, TimeUnit, TimeoutException}

import akka.event

import scala.collection.immutable.Queue

case class Address(host: String, port: Int)
case class SbtConfig(config: String)

case class UserTrace(ip: String, user: Option[User])

case class InputsWithIpAndUser(inputs: Inputs, user: UserTrace)
case class EnsimeRequestEnvelop(request: EnsimeRequest, user: UserTrace)

case class RunSnippet(inputs: InputsWithIpAndUser)
case class SaveSnippet(inputs: InputsWithIpAndUser)
case class AmendSnippet(snippetId: SnippetId, inputs: InputsWithIpAndUser)
case class UpdateSnippet(snippetId: SnippetId, inputs: InputsWithIpAndUser)
case class DeleteSnippet(snippetId: SnippetId)
case class DownloadSnippet(snippetId: SnippetId)

case class ForkSnippet(snippetId: SnippetId, inputs: InputsWithIpAndUser)

case class FetchSnippet(snippetId: SnippetId)
case class FetchOldSnippet(id: Int)
case class FetchUserSnippets(user: User)

case class ReceiveStatus(requester: ActorRef)

case class Run(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId)

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
  private val ensimePortsStart = config.getInt("remote-ensime-ports-start")
  private val ensimePortsSize = config.getInt("remote-ensime-ports-size")

  val ensimeConfig =
    ConfigFactory.load().getConfig("com.olegych.scastie.ensime")

  val ensimeSbtReloadTimeout = {
    val timeunit = TimeUnit.SECONDS
    FiniteDuration(
      ensimeConfig.getDuration("sbtReloadTimeout", timeunit),
      timeunit
    )
  }

  val sbtConfig = ConfigFactory.load().getConfig("com.olegych.scastie.sbt")

  val sbtRunTimeout = {
    val timeunit = TimeUnit.SECONDS
    FiniteDuration(
      sbtConfig.getDuration("runTimeout", timeunit),
      timeunit
    )
  }

  val sbtReloadTimeout = {
    val timeunit = TimeUnit.SECONDS
    FiniteDuration(
      sbtConfig.getDuration("sbtReloadTimeout", timeunit),
      timeunit
    )
  }

  private val sbtPorts = (0 until sbtPortsSize).map(sbtPortsStart + _)
  private val ensimePorts = (0 until ensimePortsSize).map(ensimePortsStart + _)

  private def connectRunner(
      runnerName: String,
      actorName: String,
      host: String
  )(port: Int): ((String, Int), ActorSelection) = {
    val selection =
      context.actorSelection(
        s"akka.tcp://$runnerName@$host:$port/user/$actorName"
      )

    selection ! ActorConnected

    (host, port) -> selection
  }

  private var remoteSbtSelections =
    sbtPorts.map(connectRunner("SbtRunner", "SbtActor", host)).toMap

  private var remoteEnsimeSelections = ensimePorts
    .map(connectRunner("EnsimeRunner", "EnsimeActor", host))
    .toMap

  val emptyHistory = History(Queue.empty[Record[Inputs]], size = 100)

  private val needsReload = (from: Inputs, to: Inputs) => from.needsReload(to)

  private var sbtLoadBalancer: SbtBalancer = {
    val sbtServers = remoteSbtSelections.to[Vector].map {
      case (_, ref) => {
        val state: SbtState = SbtState.Unknown
        Server.of[SbtRunTaskId](ref, Inputs.default, state)
      }
    }

    val sbtTaskCost = sbtRunTimeout / 2

    LoadBalancer(
      servers = sbtServers,
      history = emptyHistory,
      taskCost = sbtTaskCost,
      reloadCost = sbtReloadTimeout,
      needsReload = needsReload
    )
  }

  updateSbtBalancer(sbtLoadBalancer)

  private var ensimeLoadBalancer: EnsimeBalancer = {
    val ensimeServers = remoteEnsimeSelections.to[Vector].map {
      case (_, ref) => {
        val state: EnsimeServerState = EnsimeServerState.Unknown
        Server.of[EnsimeTaskId](
          ref,
          Inputs.default,
          state
        )
      }
    }

    val ensimeTaskCost = 3.seconds

    LoadBalancer(
      servers = ensimeServers,
      history = emptyHistory,
      taskCost = ensimeTaskCost,
      reloadCost = ensimeSbtReloadTimeout,
      needsReload = needsReload
    )
  }

  updateEnsimeBalancer(ensimeLoadBalancer)

  import context._

  system.scheduler.schedule(0.seconds, 30.seconds) {
    implicit val timeout: Timeout = Timeout(10.seconds)
    try {
      Await.result(
        Future.sequence(sbtLoadBalancer.servers.map(_.ref ? SbtPing)),
        15.seconds
      )

      Await.result(
        Future.sequence(ensimeLoadBalancer.servers.map(_.ref ? SbtPing)),
        10.seconds
      )

      ()
    } catch {
      case e: TimeoutException => ()
    }
  }

  override def preStart(): Unit = {
    statusActor ! SetDispatcher(self)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    container.es.shutdown()
  }

  private val container = new SnippetsContainer(
    Paths.get(config.getString("snippets-dir")),
    Paths.get(config.getString("old-snippets-dir"))
  )(
    ExecutionContext.fromExecutorService(
      Executors.newCachedThreadPool()
    )
  )

  private val portsInfo = sbtPorts.mkString("\nsbt: [", ", ", "]") + ensimePorts
    .mkString("\nensime: [", ", ", "]")
  log.info(s"connecting to: $host $portsInfo")

  private def updateSbtBalancer(newSbtBalancer: SbtBalancer): Unit = {
    if (sbtLoadBalancer != newSbtBalancer) {
      statusActor ! SbtLoadBalancerUpdate(newSbtBalancer)
    }
    sbtLoadBalancer = newSbtBalancer
    ()
  }

  private def updateEnsimeBalancer(newEnsimeBalancer: EnsimeBalancer): Unit = {
    if (ensimeLoadBalancer != newEnsimeBalancer) {
      statusActor ! EnsimeLoadBalancerUpdate(newEnsimeBalancer)
    }
    ensimeLoadBalancer = newEnsimeBalancer
    ()
  }

  //can be called from future
  private def run(inputsWithIpAndUser: InputsWithIpAndUser,
                  snippetId: SnippetId): Unit = {
    self ! Run(inputsWithIpAndUser, snippetId)
  }
  //cannot be called from future
  private def run0(inputsWithIpAndUser: InputsWithIpAndUser,
                   snippetId: SnippetId): Unit = {

    val InputsWithIpAndUser(inputs, UserTrace(ip, user)) = inputsWithIpAndUser

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, inputs)

    val task = Task(inputs, Ip(ip), SbtRunTaskId(snippetId))

    sbtLoadBalancer.add(task) match {
      case Some((server, newBalancer)) => {
        updateSbtBalancer(newBalancer)

        server.ref.tell(
          SbtTask(snippetId, inputs, ip, user.map(_.login), progressActor),
          self
        )
      }
      case _ => ()
    }
  }

  def receive: Receive = event.LoggingReceive(event.Logging.InfoLevel) {

    case EnsimeRequestEnvelop(request, UserTrace(ip0, user)) => {
      val taskId = EnsimeTaskId.create
      log.info("id: {}, ip: {} task: {}", taskId, ip0, request)

      val ip = Ip(ip0)

      val task = Task(request.inputs, ip, taskId)

      ensimeLoadBalancer.add(task) match {
        case Some((server, newBalancer)) => {
          updateEnsimeBalancer(newBalancer)

          implicit val timeout = Timeout(20.seconds)
          val senderRef = sender()

          (server.ref ? EnsimeTaskRequest(request, taskId))
            .mapTo[EnsimeTaskResponse]
            .map { taskResponse =>
              updateEnsimeBalancer(
                ensimeLoadBalancer.done(taskResponse.taskId)
              )
              senderRef ! taskResponse.response
            }
        }
        case None => {
          sender ! None
        }
      }
    }

    case EnsimeTaskResponse(response, taskId) => {
      updateEnsimeBalancer(ensimeLoadBalancer.done(taskId))
    }

    case SbtPong => ()

    case format: FormatRequest => {
      val server = sbtLoadBalancer.getRandomServer
      server.ref.tell(format, sender)
      ()
    }

    case x @ RunSnippet(inputsWithIpAndUser) => {
      log.info(s"starting ${x}")
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
      val sender = this.sender
      container.create(inputs, user.map(u => UserLogin(u.login))).map {
        snippetId =>
          run(inputsWithIpAndUser, snippetId)
          log.info(s"finished ${x}")
          sender ! snippetId
      }
    }

    case SaveSnippet(inputsWithIpAndUser) => {
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
      val sender = this.sender
      container.save(inputs, user.map(u => UserLogin(u.login))).map {
        snippetId =>
          run(inputsWithIpAndUser, snippetId)
          sender ! snippetId
      }
    }

    case AmendSnippet(snippetId, inputsWithIpAndUser) => {
      val sender = this.sender
      container.amend(snippetId, inputsWithIpAndUser.inputs).map {
        amendSuccess =>
          if (amendSuccess) {
            run(inputsWithIpAndUser, snippetId)
          }
          sender ! amendSuccess
      }
    }

    case UpdateSnippet(snippetId, inputsWithIpAndUser) => {
      val sender = this.sender
      container.update(snippetId, inputsWithIpAndUser.inputs).map {
        updatedSnippetId =>
          updatedSnippetId.foreach(
            snippetIdU => run(inputsWithIpAndUser, snippetIdU)
          )
          sender ! updatedSnippetId
      }
    }

    case ForkSnippet(snippetId, inputsWithIpAndUser) => {
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) =
        inputsWithIpAndUser
      val sender = this.sender
      container
        .fork(snippetId, inputs, user.map(u => UserLogin(u.login)))
        .map {
          case Some(forkedSnippetId) =>
            sender ! Some(forkedSnippetId)
            run(inputsWithIpAndUser, forkedSnippetId)
          case None => sender ! None
        }
    }

    case DeleteSnippet(snippetId) => {
      val sender = this.sender
      container.delete(snippetId).map(_ => sender ! (()))
    }

    case DownloadSnippet(snippetId) => {
      val sender = this.sender
      container.downloadSnippet(snippetId).map(sender ! _)
    }

    case FetchSnippet(snippetId) => {
      val sender = this.sender
      container.readSnippet(snippetId).map(sender ! _)
    }

    case FetchOldSnippet(id) => {
      val sender = this.sender
      container.readOldSnippet(id).map(sender ! _)
    }

    case FetchUserSnippets(user) => {
      val sender = this.sender
      container.listSnippets(UserLogin(user.login)).map(sender ! _)
    }

    case FetchScalaJs(snippetId) => {
      val sender = this.sender
      container.readScalaJs(snippetId).map(sender ! _)
    }

    case FetchScalaSource(snippetId) => {
      val sender = this.sender
      container.readScalaSource(snippetId).map(sender ! _)
    }

    case FetchScalaJsSourceMap(snippetId) => {
      val sender = this.sender
      container.readScalaJsSourceMap(snippetId).map(sender ! _)
    }

    case progress: api.SnippetProgress => {
      if (progress.isDone) {
        progress.snippetId.foreach(
          sid => updateSbtBalancer(sbtLoadBalancer.done(SbtRunTaskId(sid)))
        )
      }
      container.appendOutput(progress)
    }

    case event: DisassociatedEvent => {
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteSbtSelections
          .get((host, port))
          .orElse(remoteEnsimeSelections.get((host, port)))
      } {
        log.warning("removing disconnected: {}", ref)
        val previousRemoteSbtSelections = remoteSbtSelections
        remoteSbtSelections = remoteSbtSelections - ((host, port))
        if (previousRemoteSbtSelections != remoteSbtSelections) {
          updateSbtBalancer(sbtLoadBalancer.removeServer(ref))
        }

        val previousRemoteEnsimeSelections = remoteEnsimeSelections
        remoteEnsimeSelections = remoteEnsimeSelections - ((host, port))
        if (previousRemoteEnsimeSelections != remoteEnsimeSelections) {
          updateEnsimeBalancer(ensimeLoadBalancer.removeServer(ref))
        }
      }
    }

    case SbtUp => {
      log.info("SbtUp")
    }

    case Replay(SbtRun(snippetId, inputs, progressActor, snippetActor)) => {
      log.info("Replay: " + inputs.code)
    }

    case SbtRunnerConnect(runnerHostname, runnerAkkaPort) => {
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
            Server.of[SbtRunTaskId](ref, Inputs.default, state)
          )
        )
      }
    }

    case EnsimeRunnerConnect(runnerHostname, runnerAkkaPort) => {
      log.info("Connected Ensime Runner {}", runnerAkkaPort)

      if (!remoteEnsimeSelections.contains((runnerHostname, runnerAkkaPort))) {
        log.info("Connected Ensime Runner {}", runnerAkkaPort)

        val sel =
          connectRunner("EnsimeRunner", "EnsimeActor", runnerHostname)(
            runnerAkkaPort
          )
        val (_, ref) = sel

        remoteEnsimeSelections = remoteEnsimeSelections + sel

        val state: EnsimeServerState = EnsimeServerState.Initializing

        updateEnsimeBalancer(
          ensimeLoadBalancer.addServer(
            Server.of[EnsimeTaskId](ref, Inputs.default, state)
          )
        )

      }
    }

    case ReceiveStatus(requester) => {
      sender ! LoadBalancerInfo(sbtLoadBalancer, ensimeLoadBalancer, requester)
    }

    case statusProgress: StatusProgress => {
      statusActor ! statusProgress
    }

    case run: Run => {
      run0(run.inputsWithIpAndUser, run.snippetId)
    }

    case ensimeState: EnsimeServerState => {
      updateEnsimeBalancer(
        ensimeLoadBalancer.copy(
          servers = ensimeLoadBalancer.servers.map(
            server => {
              val serverPort = server.ref.anchorPath.address.port
              val senderPort = sender.path.address.port

              if (serverPort == senderPort) {
                // clear tasks if we are switching configuration
                val mailbox =
                  if (ensimeState == EnsimeServerState.Ready) Queue()
                  else server.mailbox

                server.copy(
                  state = ensimeState,
                  mailbox = mailbox
                )
              } else {
                server
              }
            }
          )
        )
      )
    }
  }
}
