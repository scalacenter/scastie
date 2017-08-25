package com.olegych.scastie.balancer

import com.olegych.scastie.SbtTask
import com.olegych.scastie.api
import com.olegych.scastie.api._
import com.olegych.scastie.storage._

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSelection}

import akka.remote.DisassociatedEvent
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.nio.file._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException

import scala.collection.immutable.Queue
import scala.util.Random
import scala.util.control.NonFatal

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

class DispatchActor(progressActor: ActorRef, statusActor: ActorRef)
    extends Actor
    with ActorLogging {
  private val configuration =
    ConfigFactory.load().getConfig("com.olegych.scastie.balancer")

  private val host = configuration.getString("remote-hostname")
  private val sbtPortsStart = configuration.getInt("remote-sbt-ports-start")
  private val sbtPortsSize = configuration.getInt("remote-sbt-ports-size")
  private val ensimePortsStart =
    configuration.getInt("remote-ensime-ports-start")
  private val ensimePortsSize = configuration.getInt("remote-ensime-ports-size")

  private val sbtPorts = (0 until sbtPortsSize).map(sbtPortsStart + _)
  private val ensimePorts = (0 until ensimePortsSize).map(ensimePortsStart + _)

  private def connectRunner(runnerName: String, actorName: String)(
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
    sbtPorts.map(connectRunner("SbtRunner", "SbtActor")(host)).toMap
  private var remoteEnsimeSelections = ensimePorts
    .map(connectRunner("EnsimeRunner", "EnsimeRunnerActor")(host))
    .toMap

  // ensime-runner -> IPs
  private var usersPerEnsime = Map[ActorPath, Set[Ip]]().withDefaultValue(Set())

  private var loadBalancer: LoadBalancer[String, ActorSelection] = {
    val sbtServers = remoteSbtSelections.to[Vector].map {
      case (_, ref) =>
        Server(ref, Inputs.default.sbtConfig)
    }
    val ensimeServers = remoteEnsimeSelections.to[Vector].map {
      case (_, ref) =>
        Server(ref, Inputs.default.sbtConfig)
    }

    val history = History(Queue.empty[Record[String]], size = 100)
    LoadBalancer(sbtServers = sbtServers,
                 ensimeServers = ensimeServers,
                 history)
  }
  updateBalancer(loadBalancer)

  import context._

  system.scheduler.schedule(0.seconds, 5.seconds) {
    implicit val timeout: Timeout = Timeout(5.seconds)
    try {
      val res =
        Await.result(
          Future.sequence(loadBalancer.sbtServers.map(_.ref ? SbtPing)),
          1.seconds
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

  private val container = new SnippetsContainer(
    Paths.get(configuration.getString("snippets-dir")),
    Paths.get(configuration.getString("old-snippets-dir"))
  )

  private val portsInfo = sbtPorts.mkString("\nsbt: [", ", ", "]") + ensimePorts
    .mkString("\nensime: [", ", ", "]")
  log.info(s"connecting to: $host $portsInfo")

  private def updateBalancer(
      newBalancer: LoadBalancer[String, ActorSelection]
  ): Unit = {
    if (loadBalancer != newBalancer) {
      statusActor ! LoadBalancerUpdate(newBalancer)
    }
    loadBalancer = newBalancer
    ()
  }

  private def run(inputsWithIpAndUser: InputsWithIpAndUser,
                  snippetId: SnippetId): Unit = {

    val InputsWithIpAndUser(inputs, UserTrace(ip, user)) = inputsWithIpAndUser

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, inputs)

    val task = Task(inputs.sbtConfig, Ip(ip), SbtRunTaskId(snippetId))

    loadBalancer.add(task) match {
      case Some((server, newBalancer)) => {
        updateBalancer(newBalancer)

        server.ref.tell(
          SbtTask(snippetId, inputs, ip, user.map(_.login), progressActor),
          self
        )
      }
      case _ => ()
    }
  }

  def receive: Receive = {

    case EnsimeRequestEnvelop(request, UserTrace(ip0, user)) =>
      val taskId = EnsimeTaskId.create
      log.info("id: {}, ip: {} task: {}", taskId, ip0, request)

      val ip = Ip(ip0)

      val task = Task(request.inputs.sbtConfig, ip, taskId)

      loadBalancer.add(task) match {
        case Some((server, newBalancer)) => {
          try {
            val ensimeRunnerPath = server.ref.anchorPath / "user" / "EnsimeRunnerActor" / "EnsimeActor"
            log.info(s"Add $ensimeRunnerPath -> $ip")
            val updatedIPs = usersPerEnsime(ensimeRunnerPath) + ip
            usersPerEnsime = usersPerEnsime + (ensimeRunnerPath -> updatedIPs)

            updateBalancer(newBalancer)

            implicit val timeout = Timeout(20.seconds)
            val senderRef = sender()

            (server.ref ? EnsimeTaskRequest(request, taskId))
              .mapTo[EnsimeTaskResponse]
              .map { taskResponse =>
                updateBalancer(loadBalancer.done(taskResponse.taskId))
                senderRef ! taskResponse.response
              }
          } catch {
            case NonFatal(_) =>
              sender ! None
              statusActor ! NotifyAllUsers(StatusEnsimeInfo(EnsimeDown))
          }
        }
        case None => {}
      }

    case EnsimeTaskResponse(response, taskId) => {
      updateBalancer(loadBalancer.done(taskId))
    }

    case SbtPong => ()

    case format: FormatRequest =>
      val server = loadBalancer.getRandomSbtServer
      server.ref.tell(format, sender)
      ()

    case RunSnippet(inputsWithIpAndUser) =>
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
      val snippetId =
        container.create(inputs, user.map(u => UserLogin(u.login)))
      run(inputsWithIpAndUser, snippetId)
      sender ! snippetId

    case SaveSnippet(inputsWithIpAndUser) =>
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
      val snippetId = container.save(inputs, user.map(u => UserLogin(u.login)))
      run(inputsWithIpAndUser, snippetId)
      sender ! snippetId

    case AmendSnippet(snippetId, inputsWithIpAndUser) =>
      val amendSuccess = container.amend(snippetId, inputsWithIpAndUser.inputs)
      if (amendSuccess) {
        run(inputsWithIpAndUser, snippetId)
      }
      sender ! amendSuccess

    case UpdateSnippet(snippetId, inputsWithIpAndUser) =>
      val updatedSnippetId =
        container.update(snippetId, inputsWithIpAndUser.inputs)

      updatedSnippetId.foreach(
        snippetIdU => run(inputsWithIpAndUser, snippetIdU)
      )

      sender ! updatedSnippetId

    case ForkSnippet(snippetId, inputsWithIpAndUser) =>
      val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser

      container
        .fork(snippetId, inputs, user.map(u => UserLogin(u.login))) match {
        case Some(forkedSnippetId) =>
          sender ! Some(forkedSnippetId)
          run(inputsWithIpAndUser, forkedSnippetId)
        case None => sender ! None
      }

    case DeleteSnippet(snippetId) =>
      container.delete(snippetId)
      sender ! (())

    case DownloadSnippet(snippetId) =>
      sender ! container.downloadSnippet(snippetId)

    case FetchSnippet(snippetId) =>
      sender ! container.readSnippet(snippetId)

    case FetchOldSnippet(id) =>
      sender ! container.readOldSnippet(id)

    case FetchUserSnippets(user) =>
      sender ! container.listSnippets(UserLogin(user.login))

    case FetchScalaJs(snippetId) =>
      sender ! container.readScalaJs(snippetId)

    case FetchScalaSource(snippetId) =>
      sender ! container.readScalaSource(snippetId)

    case FetchScalaJsSourceMap(snippetId) =>
      sender ! container.readScalaJsSourceMap(snippetId)

    case progress: api.SnippetProgress =>
      if (progress.done) {
        progress.snippetId.foreach(
          sid => updateBalancer(loadBalancer.done(SbtRunTaskId(sid)))
        )
      }
      container.appendOutput(progress)

    case event: DisassociatedEvent =>
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteSbtSelections
          .get((host, port))
          .orElse(remoteEnsimeSelections.get((host, port)))
      } {
        log.warning("removing disconnected: {}", ref)
        remoteSbtSelections = remoteSbtSelections - ((host, port))
        remoteEnsimeSelections = remoteEnsimeSelections - ((host, port))
        updateBalancer(loadBalancer.removeServer(ref))
      }

    case SbtRunnerConnect(runnerHostname, runnerAkkaPort) => {
      if (!remoteSbtSelections.contains((runnerHostname, runnerAkkaPort))) {
        log.info("Connected Runner {}", runnerAkkaPort)

        val sel =
          connectRunner("SbtRunner", "SbtActor")(runnerHostname)(runnerAkkaPort)
        val (_, ref) = sel

        remoteSbtSelections = remoteSbtSelections + sel

        updateBalancer(
          loadBalancer.addSbtServer(
            Server(ref, Inputs.default.sbtConfig)
          )
        )
      }
    }

    case EnsimeRunnerConnect(runnerHostname, runnerAkkaPort) => {
      if (!remoteEnsimeSelections.contains((runnerHostname, runnerAkkaPort))) {
        log.info("Connected Ensime Runner {}", runnerAkkaPort)

        val sel = connectRunner("EnsimeRunner", "EnsimeRunnerActor")(
          runnerHostname
        )(runnerAkkaPort)
        val (_, ref) = sel

        remoteEnsimeSelections = remoteEnsimeSelections + sel

        updateBalancer(
          loadBalancer.addEnsimeServer(
            Server(ref, Inputs.default.sbtConfig)
          )
        )
      }
    }

    case ReceiveStatus(requester) => {
      sender ! LoadBalancerInfo(loadBalancer, requester)
    }

    case statusProgress: StatusProgress =>
      log.info(s"Got status progress from ${sender.path}")
      log.info(s"Send $statusProgress to users: ${usersPerEnsime(sender.path)}")
      statusActor ! NotifyUsers(usersPerEnsime(sender.path), statusProgress)
  }
}
