Scala pastebin.
===============
http://scastie.org

Goals
-----
-  enhance communication and collaborative debugging by providing
   extensive insight in pasted code
-  fast and easy to use, no ads, no registration

Current Features
----------------
-  highlighting with Scala X-Ray
-  reusing sbt instances for fast compilation
-  running in sandbox with sbt
-  detect fragment pastes and wrap them in top level declaration so that
   they will be compilable and runnable
-  apply scalariform and linter to paste
-  building with sbt, with support for inline dependency specification
   (see https://github.com/harrah/xsbt/wiki/Scripts, only
   libraryDependencies setting is allowed)
-  distributes workload amongst multiple sbt instances (including remote)

Plans
-----
-  realtime update of paste compilation/running progress
-  allow specifying scala version
-  timeout long-running pastes
-  allow pastes deletion
-  detect pastes to gist.github.com pastie.net pastebin.com etc with
   multibot and copy to scastie
-  interface for uploading pre-sxred libraries
-  linking with lots of sxr-processed libraries
-  support execution of tests
-  link stacktraces and compilation errors to source
-  optionally load linked sources side-by-side with paste
-  evaluate pastes with no dependencies in multibot
-  generate seo friendly names from paste content
-  support repl mode (one paste can depend on another)
-  cloning pastes akin to github gists

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
-  renderer - encapsulates pastes storage and processing

For each paste create/read request Pastes controller in scastie module creates a message and sends it to PastesActor
in renderer module. The PastesActor creates a few worker RendererActors via akka router and delegates the actual
paste processing to them. But before handing off the work to delegatee, PastesActor stores unprocessed paste
and replies to Pastes controller so that it could be displayed to user immediately.

RendererActor interacts with locally running sbt instance via process I\O streams, which is quite hacky, but works
good enough. If the sbt instance terminates or otherwise encounters an error while processing a paste, the actor will be restarted.

Currently the pastes are stored directly on file system on master node (the one which is running web application
and PastesActor). This works well on free hosting like OpenShift, but obviously will not scale.
Migrating to another storage will be relatively painless because all operations are encapsulated within PastesContainer.

Instructions that detail how the project is compiled, deployed and used
---------------------------------
As this is a regular Play2 application all standard techniques apply.
For convenience sbt launch scripts for cygwin (xsbt.cmd) and *nix (xsbt.sh) are included in repository.

To start application in dev mode just execute './xsbt.sh run' and go to http://localhost:9000

To start application in debug mode in intellij on cygwin use the included 'scastie-play-debug' run configuration.

Currently the application contains separate configuration tailored for deployment on OpenShift. .openshift directory contains hooks to build and start app on git receive. Openshift specific configs are stored in
openshift*.conf files and are applied when the run script detect specific environment properties.

To test how application will behave when deployed with OpenShift configs, use test-openshift.sh script which
sets mocks OpenShift environment and starts the post-receive hook.

The remote workers can be started via RendererMain class in renderer module. This can be done via sbt or via
intellij, or via the same post-receive hook which starts main application if OPENSHIFT_APP_NAME env property matches "renderer". The urls of remote worker should be specified when running main application in *actors.conf.