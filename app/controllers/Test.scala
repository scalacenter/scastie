package controllers

import com.typesafe.config.ConfigFactory
import java.io.File
import akka.actor.Props
import com.olegych.scastie.{PastesContainer, PastesActor}
import com.olegych.scastie.PastesActor.AddPaste

/**
 */
object Test extends App {
  val system = akka.actor.ActorSystem("actors",
    ConfigFactory.load(getClass.getClassLoader, "application-renderer"))
  val pastesDir = new File("./target/pastes/")
  val renderer = system.actorOf(Props(new PastesActor(PastesContainer(pastesDir))), "pastes")
  renderer ! AddPaste("hello")
}