// source: https://contributors.scala-lang.org/t/contest-scala-lang-org-frontpage-code-snippet/1141/22?u=masseguillaume

trait Feature 

case class ObjectOriented(description: String) extends Feature
case class Functional(description: String) extends Feature
case class StaticallyTyped(description: String) extends Feature

def colorize(feature: Feature): Feature = feature match {
    case ObjectOriented(description) =>
        ObjectOriented( s"${Console.CYAN_B} ${Console.RED} ${description} ${Console.RESET}" )
    case Functional(description) =>
        Functional( s"${Console.MAGENTA_B} ${Console.GREEN} ${description} ${Console.RESET}" )
    case StaticallyTyped(description) =>
        StaticallyTyped( s"${Console.YELLOW_B} ${Console.BLUE} ${description} ${Console.RESET}" )
}

val scalaFeatures = List(
    ObjectOriented("Define new types and behaviors using classes and traits"),
    Functional("Define functions as values, pass them as higher-order functions or return them as curried functions"),
    StaticallyTyped("Programs are statically checked for safe and correct type usage at compile time")
)

scalaFeatures.map(colorize).foreach(println)