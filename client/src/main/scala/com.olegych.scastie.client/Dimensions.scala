package com.olegych.scastie.client

object Dimensions {
  def default = Dimensions(
    dimensionsHaveChanged = false,
    topBarHeight = 0,
    editorTopBarHeight = 0,
    sideBarWidth = 0,
    sideBarMinHeight = 0,
    consoleBarHeight = 0,
    consoleHeight = 0)
}

case class Dimensions(
  dimensionsHaveChanged: Boolean,
  topBarHeight: Double,
  editorTopBarHeight: Double,
  sideBarWidth: Double,
  sideBarMinHeight: Double,
  consoleBarHeight: Double,
  consoleHeight: Double)


