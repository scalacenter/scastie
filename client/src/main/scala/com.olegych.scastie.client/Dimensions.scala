package com.olegych.scastie.client

object Dimensions {
  def default =
    Dimensions(dimensionsHaveChanged = false,
               minWindowWidth = 1160,
               minWindowHeight = 800,
               forcedDesktop = false,
               topBarHeight = 0,
               editorTopBarHeight = 0,
               sideBarWidth = 0,
               sideBarMinHeight = 0,
               consoleBarHeight = 0,
               consoleHeight = 0,
               mobileBarHeight = 0)
}

case class Dimensions(dimensionsHaveChanged: Boolean,
                      minWindowWidth: Int,
                      minWindowHeight: Int,
                      forcedDesktop: Boolean,
                      topBarHeight: Int,
                      editorTopBarHeight: Int,
                      sideBarWidth: Int,
                      sideBarMinHeight: Int,
                      consoleBarHeight: Int,
                      consoleHeight: Int,
                      mobileBarHeight: Int)
