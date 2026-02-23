package org.scastie

package object api {
  type SbtRunnerState = RunnerState[SbtInputs]
  type ScalaCliRunnerState = RunnerState[ScalaCliInputs]
}
