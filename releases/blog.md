# Introducing Scastie - An interactive playground for Scala.

The Scala Center team is happy to announce the Beta of Scastie.

Aleh Aleshka ([OlegYch](https://github.com/OlegYch/)) is the original author of this project. His goal was to create a collaborative debugging tool where you can share and reproduce bugs. The Scala Center has extended Scastie to become an interactive environment for the community.

# What can I do with Scastie?

Scastie can run any Scala program with any library in your browser. You don't need to download or install anything.

[![scastie](scastie.png)](scastie.png)

We run your code in an isolated Java Virtual Machine on our servers. We allow you to specify the scala version, add libraries and much more. By default, we instrument your code to provide a REPL like environment. You can also format your code with scala-fmt. We integrated a [scaladex](https://index.scala-lang.org/) interface to allow you to search the Scala ecosystem and include any published Scala library in your project. You don't need to remember the lastest version of a specific library anymore!

Let's see it in action.

<iframe width="610" height="315" src="https://www.youtube.com/embed/ugFgdncsxEQ" frameborder="0" allowfullscreen></iframe>

In this exampe, we use scrimage, which is a simple image library. We download an image, apply a sepia filter and serve both images back to the browser.

# How does Scastie work?

When a user evaluates their code, the browser sends all its input to our server. Based on the configuration, it will forward the evaluation to an sbt instance. The output will be streamed back to the user. A specific protocol will allow the client to interpret different events such as compiler errors, runtime exceptions, instrumentation, console output, etc. 

Using sbt behind the scenes makes it possible for us to support a large range of platforms (Scalac, Dotty, Scala.js, Scala-Native and Typelevel's Scala). In our beta, we've included support for any version of Scalac as well as all published versions of Dotty. Support for other platforms, such as Scala.js or Scala Native is forthcoming. Basing Scastie on sbt allows us to support newer Scala versions and resolve libraries dependencies.

We also enable a worksheet mode, which feels much like a worksheet in an IDE. This lets a user write code as top-level expressions, without having to put code inside of a class or object with a main method. Worksheet mode gives you two ways to interleave your results; on the one hand, when an expression is evaluated, you can see the value and type of the evaluated expression to the right of the code that you wrote. On the other hand, worksheet mode also makes it possible to do a kind of literate programming; that is, you may interleave code and HTML blocks much like in notebook environments like iPython notebooks.

# What's next? 

* Improve the sharing model. For example, we could use a similar approach as gist or JS Fiddle where you can fork and edit code snippets. 
* Support Scala.js and Scala-Native. Vote on which one you'd like to see first! (Vote for Scala.js or vote for Scala Native)
* Make it possible embed Scastie in your project's documentation.

# Want to try it?

Since Scastie is a new project, we want to work out the kinks with it before everyone jumps on it and starts using it. In particular, the UI is currently a bit rough, and we're working on catching bugs in the general usage of Scastie.

For that reason, we're opening up Scastie to a handful of beta users. Do you want to help us work out the kinks with Scastie? If so, head over to [https://scastie.scala-lang.org](https://scastie.scala-lang.org) to join the beta. We'll gradually be adding more users, so if you don't get immediate access, just hang on for a day or so and we'll ping you to let you know when you can try out Scastie.

And remember, please give us feedback! Let us know what is confusing, if something doesn't work as expected, or if there's anything you think we can improve! There are links in Scastie back to our Gitter channels and GitHub issue tracker.

# Talk to us!

Thougts or opinions about Scastie? Join us over on [Scala Contributors](https://contributors.scala-lang.org) to contribute to the discussion.
