package com.olegych.scastie
package api

import scala.collection.immutable.Queue

sealed trait StatusProgress
case object StatusKeepAlive extends StatusProgress
case class StatusRunnersInfo(runners: Vector[Runner]) extends StatusProgress
case class StatusEnsimeInfo(ensimeStatus: EnsimeStatus) extends StatusProgress

sealed trait EnsimeStatus
case object EnsimeDown extends EnsimeStatus
case object EnsimeRestarting extends EnsimeStatus
case object EnsimeUp extends EnsimeStatus

case class Runner(tasks: Queue[TaskId])
