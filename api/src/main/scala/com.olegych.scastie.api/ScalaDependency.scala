package com.olegych.scastie.api

case class ScalaDependency(
    groupId: String,
    artifact: String,
    target: ScalaTarget,
    version: String
) {

  override def toString: String = target.renderSbt(this)
}