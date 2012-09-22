seq(giter8Settings: _*)

G8Keys.properties in G8Keys.g8 in Compile := Map(("name", "helloname"))

sources in (Compile, G8Keys.g8) ~= { (src) =>
  src filter { (f) =>
    !List(".*target.*", ".*\\.idea.*", ".*\\.gitignore.*").exists(r => r.r.unapplySeq(f.getPath).isDefined)
  }
}

//to be able to detect prompt
shellPrompt := (_ => ">\n")

traceLevel := 1000