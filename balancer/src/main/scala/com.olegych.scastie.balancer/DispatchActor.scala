package com.olegych.scastie.balancer

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl._
import com.olegych.scastie.api
import com.olegych.scastie.api._
import com.olegych.scastie.balancer.DispatchActor._
import com.olegych.scastie.storage._
import com.olegych.scastie.util.ConfigLoaders._
import com.olegych.scastie.util._
import com.typesafe.sslconfig.util.{ConfigLoader, EnrichedConfig}
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Try}

case class UserTrace(ip: String, user: Option[User])

case class InputsWithIpAndUser(inputs: Inputs, user: UserTrace)

case class RunSnippet(replyTo: ActorRef[SnippetId], inputs: InputsWithIpAndUser) extends Message
case class SaveSnippet(replyTo: ActorRef[SnippetId], inputs: InputsWithIpAndUser) extends Message
case class UpdateSnippet(replyTo: ActorRef[Option[SnippetId]], snippetId: SnippetId, inputs: InputsWithIpAndUser) extends Message
case class DeleteSnippet(replyTo: ActorRef[Boolean], snippetId: SnippetId) extends Message
case class DownloadSnippet(replyTo: ActorRef[Option[Path]], snippetId: SnippetId) extends Message

case class ForkSnippet(replyTo: ActorRef[Option[SnippetId]], snippetId: SnippetId, inputs: InputsWithIpAndUser) extends Message

case class FetchSnippet(replyTo: ActorRef[Option[FetchResult]], snippetId: SnippetId) extends Message
case class FetchOldSnippet(replyTo: ActorRef[Option[FetchResult]], id: Int) extends Message
case class FetchUserSnippets(replyTo: ActorRef[List[SnippetSummary]], user: User) extends Message

case class ReceiveStatus(replyTo: ActorRef[LoadBalancerInfo], requester: ActorRef[StatusProgress]) extends Message

object DispatchActor {
  type Message = BalancerMessage

  private case class Run(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId) extends Message

  private case class Done(progress: api.SnippetProgress, retries: Int) extends Message

  private case class ListingResponse(listing: Receptionist.Listing) extends Message

  object Adapter {
    case class FetchScalaJs(replyTo: ActorRef[Option[FetchResultScalaJs]], snippetId: SnippetId) extends Message
    case class FetchScalaSource(replyTo: ActorRef[Option[FetchResultScalaSource]], snippetId: SnippetId) extends Message
    case class FetchScalaJsSourceMap(replyTo: ActorRef[Option[FetchResultScalaJsSourceMap]], snippetId: SnippetId) extends Message
  }

  def apply(
    progressActor: ActorRef[ProgressActor.Message],
    statusActor: ActorRef[StatusActor.Message],
    config: BalancerConf
  ): Behavior[Message] =
    Behaviors.supervise[Message] {
      Behaviors.setup { ctx =>
        Behaviors.withTimers { timers =>
          Behaviors.logMessages(
            LogOptions().withLevel(Level.INFO),
            new DispatchActor(progressActor, statusActor, config)(ctx, timers)
          )
        }
      }
    }.onFailure(SupervisorStrategy.resume)
}

class DispatchActor(
  progressActor: ActorRef[ProgressActor.Message],
  statusActor: ActorRef[StatusActor.Message],
  config: BalancerConf
)(ctx: ActorContext[Message],
  timers: TimerScheduler[Message]
) extends AbstractBehavior(ctx) {
  import context.{executionContext, log, messageAdapter, self, system}

  // context.log is not thread safe
  // https://doc.akka.io/docs/akka/current/typed/logging.html#how-to-log
  private val safeLog = LoggerFactory.getLogger(classOf[DispatchActor])

  system.receptionist ! Receptionist.Subscribe(
    Services.SbtRunner,
    messageAdapter[Receptionist.Listing](ListingResponse)
  )

  private var remoteRunners = Set.empty[ActorRef[SbtMessage]]

  private var sbtLoadBalancer: SbtBalancer = LoadBalancer(Vector.empty)

  statusActor ! SetDispatcher(self)

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = {
    case PostStop =>
      container.close()
      Behaviors.unhandled
  }

  private val container =
    config.snippetsContainer match {
      case SnippetsType.Memory => new InMemorySnippetsContainer
      case SnippetsType.Mongo(uri) =>
        new MongoDBSnippetsContainer(uri, ExecutionContext.fromExecutor(Executors.newWorkStealingPool()))
      case f: SnippetsType.Files =>
        new FilesSnippetsContainer(
          f.snippetsDir,
          f.oldSnippetsDir
        )(
          ExecutionContext.fromExecutorService(
            Executors.newCachedThreadPool()
          )
        )
      case _ =>
        println("fallback to in-memory container")
        new InMemorySnippetsContainer
    }

  private def updateSbtBalancer(newSbtBalancer: SbtBalancer): Unit = {
    if (sbtLoadBalancer != newSbtBalancer) {
      statusActor ! SbtLoadBalancerUpdate(newSbtBalancer)
    }
    sbtLoadBalancer = newSbtBalancer
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

        server.ref ! SbtTask(snippetId, inputs, ip, user.map(_.login), progressActor, self)
      case _ => ()
    }
  }

  private val logError: PartialFunction[Try[_], _] = {
    case Failure(e) => safeLog.error("failed future", e)
  }

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case format: FormatReq =>
        val server = sbtLoadBalancer.getRandomServer
        server.foreach(_.ref ! format)

      case RunSnippet(sender, inputsWithIpAndUser) =>
        val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
        container.create(inputs, user.map(u => UserLogin(u.login))).map { snippetId =>
          sender ! snippetId
          run(inputsWithIpAndUser, snippetId)
        }.andThen(logError)

      case SaveSnippet(sender, inputsWithIpAndUser) =>
        val InputsWithIpAndUser(inputs, UserTrace(_, user)) = inputsWithIpAndUser
        container.save(inputs, user.map(u => UserLogin(u.login))).map { snippetId =>
          sender ! snippetId
          run(inputsWithIpAndUser, snippetId)
        }.andThen(logError)

      case UpdateSnippet(sender, snippetId, inputsWithIpAndUser) =>
        container.update(snippetId, inputsWithIpAndUser.inputs).map { updatedSnippetId =>
          sender ! updatedSnippetId
          updatedSnippetId.foreach(
            snippetIdU => run(inputsWithIpAndUser, snippetIdU)
          )
        }.andThen(logError)

      case ForkSnippet(sender, snippetId, inputsWithIpAndUser) =>
        val InputsWithIpAndUser(inputs, UserTrace(_, user)) =
          inputsWithIpAndUser
        container
          .fork(snippetId, inputs, user.map(u => UserLogin(u.login)))
          .map { forkedSnippetId =>
            sender ! Some(forkedSnippetId)
            run(inputsWithIpAndUser, forkedSnippetId)
          }.andThen(logError)

      case DeleteSnippet(sender, snippetId) =>
        container.deleteAll(snippetId).map(_ => sender ! true).andThen(logError)

      case DownloadSnippet(sender, snippetId) =>
        container.downloadSnippet(snippetId).map(sender ! _).andThen(logError)

      case FetchSnippet(sender, snippetId) =>
        container.readSnippet(snippetId).map(sender ! _).andThen(logError)

      case FetchOldSnippet(sender, id) =>
        container.readOldSnippet(id).map(sender ! _).andThen(logError)

      case FetchUserSnippets(sender, user) =>
        container.listSnippets(UserLogin(user.login)).map(sender ! _).andThen(logError)

      case Adapter.FetchScalaJs(sender, snippetId) =>
        container.readScalaJs(snippetId).map(sender ! _).andThen(logError)

      case Adapter.FetchScalaSource(sender, snippetId) =>
        container.readScalaSource(snippetId).map(sender ! _).andThen(logError)

      case Adapter.FetchScalaJsSourceMap(sender, snippetId) =>
        container.readScalaJsSourceMap(snippetId).map(sender ! _).andThen(logError)

      case SnippetProgressAsk(progress) =>
        if (progress.isDone) {
          self ! Done(progress, retries = 100)
        }
        container
          .appendOutput(progress)
          .recover {
            case e => log.error(s"failed to save $progress", e)
          }
          .andThen(logError)

      case done: Done =>
        done.progress.snippetId.foreach { sid =>
          val newBalancer = sbtLoadBalancer.done(TaskId(sid))
          newBalancer match {
            case Some(newBalancer) =>
              updateSbtBalancer(newBalancer)
            case None =>
              if (done.retries >= 0) {
                timers.startSingleTimer(
                  done.copy(retries = done.retries - 1),
                  1.second
                )
              } else {
                val taskIds =
                  sbtLoadBalancer.servers.flatMap(_.mailbox.map(_.taskId))
                log.error(s"stopped retrying to update ${taskIds} with ${done}")
              }
          }
        }

      case ListingResponse(Services.SbtRunner.Listing(listings)) =>
        val added = listings diff remoteRunners
        val removed = remoteRunners diff listings
        if (added.nonEmpty) {
          log.info("Runners added {}", added)
        }
        if (removed.nonEmpty){
          log.warn("Runners removed {}", removed)
        }
        if (added.nonEmpty || removed.nonEmpty) {
          remoteRunners = listings
          val newBalancer =  LoadBalancer(
            sbtLoadBalancer.servers.filterNot(s => removed.contains(s.ref)) ++
              added.map(Server(_, Inputs.default, SbtState.Unknown: SbtState))
          )

          updateSbtBalancer(newBalancer)
        }
        // only for testing
        if (!serviceRegistered && remoteRunners.nonEmpty) {
          ctx.system.receptionist ! Receptionist.Register(Services.Balancer, ctx.self)
          serviceRegistered = true
        }

      case ReceiveStatus(replyTo, requester) =>
        replyTo ! LoadBalancerInfo(sbtLoadBalancer, requester)

      case run: Run =>
        run0(run.inputsWithIpAndUser, run.snippetId)
    }

    this
  }

  private[this] var serviceRegistered = false
}

case class BalancerConf(
  snippetsContainer: SnippetsType,
)
object BalancerConf {
  import SnippetsType._
  implicit val loader: ConfigLoader[BalancerConf] = (c: EnrichedConfig) => BalancerConf(
    c.get[String]("snippets-container.type") match {
      case "mongo"  => Mongo(c.get[String]("snippets-container.uri"))
      case "files"  => c.get[Files]("snippets-container")
      case "memory" => Memory
    }
  )
}

sealed trait SnippetsType
object SnippetsType {
  case object Memory extends SnippetsType

  case class Mongo(uri: String) extends SnippetsType

  case class Files(
    snippetsDir: Path,
    oldSnippetsDir: Path,
  ) extends SnippetsType
  object Files {
    implicit val loader: ConfigLoader[Files] = (c: EnrichedConfig) => Files(
      c.get[Path]("snippets-dir"),
      c.get[Path]("old-snippets-dir"),
    )
  }
}
