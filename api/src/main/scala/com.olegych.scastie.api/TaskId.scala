package com.olegych.scastie
package api

import java.util.UUID
import play.api.libs.json._

sealed trait TaskId {
  def cost: Int
}

case class SbtRunTaskId(snippetId: SnippetId) extends TaskId {
  //([0s, 30s] upper bound Defined in SbtMain)
  def cost: Int = 15 // s
}

object EnsimeTaskId {
  def create = EnsimeTaskId(UUID.randomUUID())
}
case class EnsimeTaskId(id: UUID) extends TaskId {
  // ensime is pretty fast when indexed
  def cost: Int = 3 // s
}

object TaskId {
  implicit object TaskIdFormat extends Format[TaskId] {
    private val formatSbtRunTaskId = Json.format[SbtRunTaskId]
    private val formatEnsimeTaskId = Json.format[EnsimeTaskId]

    def writes(taskId: TaskId): JsValue = {
      taskId match {
        case sbtRunTaskId: SbtRunTaskId => {
          formatSbtRunTaskId.writes(sbtRunTaskId).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("SbtRunTaskId")))
        }

        case ensimeTaskId: EnsimeTaskId => {
          formatEnsimeTaskId.writes(ensimeTaskId).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("EnsimeTaskId")))
        }
      }
    }

    def reads(json: JsValue): JsResult[TaskId] = {
      json match {
        case obj: JsObject => {
          val vs = obj.value

          vs.get("$type") match {
            case Some(tpe) => {
              tpe match {
                case JsString("SbtRunTaskId") => formatSbtRunTaskId.reads(json)
                case JsString("EnsimeTaskId") => formatEnsimeTaskId.reads(json)
                case _                        => JsError(Seq())
              }
            }
            case None => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }
}
