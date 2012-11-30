Scala pastebin.
===============

URL
---
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
   they will be compilable andrunnable
-  apply scalariform and linter to paste
-  building with sbt, with support for inline dependency specification
   (see https://github.com/harrah/xsbt/wiki/Scripts, only
   libraryDependencies setting is allowed)

Plans
-----
-  timeout long-running pastes
-  linking with lots of sxr-processed libraries
-  support execution of tests
-  spawn multiple sbt instances
-  detect pastes to gist.github.com pastie.net pastebin.com etc with
   multibot and copy to scastie
-  allow pastes deletion
-  link stacktraces and compilation errors to source
-  optionally load linked sources side-by-side with paste
-  evaluate pastes with no dependencies in multibot
-  interface for uploading pre-sxred libraries
-  generate seo friendly names from paste content
-  support repl mode (one paste can depend on another)