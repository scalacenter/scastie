import base.TemplatePastes
import play.api._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")
    TemplatePastes.templates.foreach { case (label, paste) =>
      controllers.Pastes.container.paste(paste.id).pasteFile.write(paste.content)
    }
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }
}
