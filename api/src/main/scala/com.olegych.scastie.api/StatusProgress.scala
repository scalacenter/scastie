package com.olegych.scastie
package api

import scala.collection.immutable.Queue

sealed trait StatusProgress
case object StatusKeepAlive extends StatusProgress
case class StatusInfo(runners: Vector[Runner]) extends StatusProgress

case class Runner(tasks: Queue[TaskId])
