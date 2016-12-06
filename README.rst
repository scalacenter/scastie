Scala pastebin.
===============
http://scastie.org | https://scastie.scala-lang.org

Goals
-----
-  enhance communication and collaborative debugging by providing
   extensive insight in pasted code
-  fast and easy to use, no ads, no registration

Current Features
----------------
-  reusing sbt instances for fast compilation
-  running in sandbox with sbt
-  building with sbt, with support for inline dependency specification
   (see http://www.scala-sbt.org/release/docs/Scripts.html, only
   libraryDependencies, scalaVersion, resolvers, scalacOptions, sbtPlugin settings are allowed)
-  distributes workload amongst multiple sbt instances (including remote)
-  realtime update of paste compilation/running progress
-  timeout long-running pastes
-  allow specifying scala version

Why it does it this way, benefits and drawbacks
------------
Scastie relies on existing scala infrastructure to provide close to real world experience inside
a pastebin-like sandbox. This minimizes the effort to create meaningful paste but might require some
prior experience to fully unleash its (and scala) power.

It is not meant to be an online IDE, but rather a collaborative debugging tool.
Some features for better collaboration are outlined in Plans section.

One drawback of such approach is that it makes it hardly useful for languages not currently supported by sbt.
This of course can be improved using language-specific sbt plugins, but at this time is not in scope.

High-level design and architecture
-----------------
The application consists of two modules:

-  scastie - standard play2 web application module
-  sbt-runner - encapsulates pastes processing

For each paste create/read request Pastes controller in scastie module creates a message and sends it to PastesActor
in renderer module. The PastesActor routes messages to a remote SbtActor via akka router.

SbtActor(s) interacts with locally running sbt instance via process I\O streams, which is quite hacky, but works
good enough. If the sbt instance terminates or otherwise encounters an error while processing a paste, the actor will be restarted.

Currently the pastes are stored directly on file system on master node (the one which is running web application
and PastesActor). This works well on free hosting like OpenShift, but obviously will not scale.
Migrating to another storage will be relatively painless because all operations are encapsulated within PastesContainer.

Instructions that detail how the project is compiled, deployed and used
---------------------------------

``
sbt
> project sbtApi
> + publishLocal
> scastie/run
``
