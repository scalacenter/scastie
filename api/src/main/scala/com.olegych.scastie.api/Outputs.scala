// package com.olegych.scastie.api

// import com.olegych.scastie.proto.{Problem, RuntimeError}

// object Outputs {
//   def default = Outputs(
//     consoleOutputs = Vector(),
//     compilationInfos = Set(),
//     instrumentations = Set(),
//     runtimeError = None,
//     sbtError = false
//   )
// }
// case class Outputs(
//     consoleOutputs: Vector[ConsoleOutput],
//     compilationInfos: Set[Problem],
//     instrumentations: Set[Instrumentation],
//     runtimeError: Option[RuntimeError],
//     sbtError: Boolean
// ) {

//   def console: String = consoleOutputs.map(_.show).mkString("\n")

//   def isClearable: Boolean =
//     consoleOutputs.nonEmpty ||
//       compilationInfos.nonEmpty ||
//       instrumentations.nonEmpty ||
//       runtimeError.isDefined
// }

// case class Position(start: Int, end: Int)

// sealed trait ConsoleOutput {
//   def show: String
// }
// object ConsoleOutput {
//   case class SbtOutput(line: String) extends ConsoleOutput {
//     def show: String = s"sbt: $line"
//   }

//   case class UserOutput(line: String) extends ConsoleOutput {
//     def show: String = line
//   }

//   case class ScastieOutput(line: String) extends ConsoleOutput {
//     def show: String = s"scastie: $line"
//   }
// }
