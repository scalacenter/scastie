seq(giter8Settings :_*)

G8Keys.properties in G8Keys.g8 in Compile := Map(("name", "helloname"))

//to be able to detect prompt
shellPrompt := (_ => ">\n")
