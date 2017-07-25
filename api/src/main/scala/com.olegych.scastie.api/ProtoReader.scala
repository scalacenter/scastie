// package com.olegych.scastie.api

// import com.olegych.scastie.schema

// object ProtoReader {
//   def fromScalaTarget(scalaTarget: schema.ScalaTarget): ScalaTarget = {
//     // import schema.ScalaTarget.{ }

//     import schema.ScalaTarget.{Value => stv}
//     import schema.{ScalaTarget => st}

//     scalaTarget.value match {
//       case pst.WrapPlainScala(st.PlainScala(scalaVersion)) =>
//         ScalaTarget.PlainScala(scalaVersion)

//       case pst.TypelevelScala(st.TypelevelScala(scalaVersion)) =>
//         ScalaTarget.TypelevelScala(scalaVersion)

//       case pst.WrapPlainScala(st.PlainScala(scalaVersion)) =>
//         ScalaTarget.PlainScala(scalaVersion)

//       case pst.WrapPlainScala(st.PlainScala(scalaVersion)) =>
//         ScalaTarget.PlainScala(scalaVersion)

//       case _ => ???
//     }
//   }
// }