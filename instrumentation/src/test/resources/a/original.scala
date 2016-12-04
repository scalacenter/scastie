class Worksheet$ {
  println(1)

  if (true) 42 else 45

  for { i <- List(1, 2, 3) } yield i + 1

  val a = 1 + 1

  var b = 2 + 2

  b = 4 + 4

  implicitly[Ordering[Int]].cmp(1, 2)
}
