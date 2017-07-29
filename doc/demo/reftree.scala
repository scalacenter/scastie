/**

resolvers ++= Seq(
  Resolver.bintrayRepo("stanch", "maven"),
  Resolver.bintrayRepo("drdozer", "maven")
)

libraryDependencies += "org.stanch" %% "reftree" % "latest-version"

*/

import reftree.render._
import reftree.diagram._
val renderer = Renderer()
renderer.render("list", Diagram(List(1, 2, 3)))
image("list.png")