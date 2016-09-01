// package controllers

// import akka.actor.Props
// import akka.pattern.ask
// import akka.util.Timeout
// import com.olegych.scastie.PastesActor.{AddPaste, DeletePaste, GetPaste, Paste}
// import com.olegych.scastie.{PastesActor, PastesContainer}
// import com.typesafe.config.ConfigFactory
// import controllers.Progress.{MonitorChannel, MonitorProgress}
// import play.api.Play
// import play.api.data.Forms._
// import play.api.data._
// import play.api.i18n.Messages
// import play.api.libs.json.JsValue
// import play.api.mvc._
// import play.twirl.api.Html

// import scala.concurrent.Future
// import scala.concurrent.duration._
// import scalaz.Scalaz._


// object Pastes extends Controller {

//   import play.api.Play.current

// import scala.concurrent.ExecutionContext.Implicits.global

//   val system = {
//     val classloader = Play.application.classloader
//     akka.actor.ActorSystem("actors",
//       ConfigFactory.load(classloader, Play.configuration.getString("actors.conf").get), classloader)
//   }

//   val progressActor = system.actorOf(Props[Progress])
//   val container = PastesContainer(new java.io.File(Play.configuration.getString("pastes.data.dir").get))
//   val renderer = system.actorOf(Props(new PastesActor(container, progressActor)), "pastes")

//   implicit val timeout = Timeout(100.seconds)

//   case class NewPaste(paste: String, id: Option[Long])

//   val pasteForm = Form(
//     mapping(
//       "paste" -> text(maxLength = 100000),
//       "id" -> optional(longNumber)
//     )(NewPaste.apply)(NewPaste.unapply)
//   )

//   def add = Action.async { implicit request =>
//     val form = pasteForm.bindFromRequest()
//     createPaste(form, Application.uid)
//   }

//   def createPaste(form: Form[NewPaste], uid: String): Future[Result] = {
//     val paste = form("paste").value.get
//     if (form.hasErrors) {
//       Future.successful(Redirect(routes.Application.index())
//         .flashing("error" -> form.errors.map(_.message).mkString, "paste" -> paste))
//     } else {
//       (renderer ? AddPaste(paste, uid)).mapTo[Paste].map { paste =>
//         Redirect(routes.Pastes.show(paste.id))
//       }
//     }
//   }

//   def edit = Action { implicit request =>
//     val form = pasteForm.bindFromRequest().get
//     //don't use id if paste content was overwritten by rendered paste
//     val pasteById = form.id.flatMap(id => container.paste(id).pasteFile.read.filterNot(isRendered))
//     Application.edit(pasteById | form.paste)
//   }

//   def delete(id: Long) = Action.async { implicit request =>
//     (renderer ? DeletePaste(id, Application.uid)).mapTo[Paste].map { paste =>
//       val result = Redirect(routes.Pastes.show(id))
//       paste.uid.cata(_ => result.flashing("error" -> Messages("invalid.user")), result)
//     }
//   }

//   def show(id: Long) = Action.async { implicit request =>
//     (renderer ? GetPaste(id)).mapTo[Paste].map { paste =>
//       val content = (paste.renderedContent orElse paste.content).orZero
//       val output = request.flash.get("error").map(_ + "\n").getOrElse("") + paste.output.getOrElse("")
//       val typedContent = if (isRendered(content)) Left(Html(content)) else Right(content)
//       val ref = """\[(?:error|warn)\].*test.scala:(\d+)""".r
//       val highlights = ref.findAllIn(output).matchData.map(_.group(1).toInt).toSeq
//       Ok(views.html.show(typedContent, output, highlights, id))
//     }
//   }
//   private def isRendered(content: String): Boolean = content.matches("(?mis)\\s*<pre>.*")
//   def progress(id: Long) = WebSocket.tryAccept[JsValue] { request =>
//     (progressActor ? MonitorProgress(id)).mapTo[MonitorChannel].map(m => Right(m.value))
//   }

// }
