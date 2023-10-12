package scastie.api

import io.circe.generic.semiauto._
import io.circe._

sealed trait SbtState extends ServerState
object SbtState {
  case object Unknown extends SbtState {
    override def toString: String = "Unknown"
    def isReady: Boolean = true
  }

  case object Disconnected extends SbtState {
    override def toString: String = "Disconnected"
    def isReady: Boolean = false
  }

  implicit val sbtStateEncoder: Encoder[SbtState] = deriveEncoder[SbtState]
  implicit val sbtStateDecoder: Decoder[SbtState] = deriveDecoder[SbtState]

  // implicit object SbtStateFormat extends Format[SbtState] {
  //   def writes(state: SbtState): JsValue = {
  //     JsString(state.toString)
  //   }

  //   private val values =
  //     List(
  //       Unknown,
  //       Disconnected
  //     ).map(v => (v.toString, v)).toMap

  //   def reads(json: JsValue): JsResult[SbtState] = {
  //     json match {
  //       case JsString(tpe) => {
  //         values.get(tpe) match {
  //           case Some(v) => JsSuccess(v)
  //           case _       => JsError(Seq())
  //         }
  //       }
  //       case _ => JsError(Seq())
  //     }
  //   }
  // }
}
