// scp scastie@scastie.scala-lang.org:users.txt users.txt
object Announce {
  import java.nio.file._

  val users = Files.readAllLines(Paths.get("users.txt")).toArray
  val slice = 50
  val from = 800

  users.drop(from).sliding(slice, slice).toList.zipWithIndex.foreach {
    case (group, i) =>
      val start = from + i * slice
      val end = start + group.size

      val message =
        s"""|Scastie Beta Opens ($start - $end)
            |
            |Hello,
            |
            |Thanks for signing up to the Beta. We are now opening spots $start to $end!
            |
            |This means you can go to https://scastie.scala-lang.org and you will now have access. Oh, and please, please, please give us feedback!
            |
            |${group.map(user => "@" + user).mkString(System.lineSeparator)}
            |
            |Thanks,
            |Guillaume""".stripMargin

      val dest = Paths.get("beta", start.toString)
      if (Files.exists(dest)) {
        Files.delete(dest)
      }
      Files.write(dest, message.getBytes, StandardOpenOption.CREATE_NEW)
  }
}
