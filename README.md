Scala pastebin.
=========================================

URL
------
http://scastie.org

Goals
---------
* enhance communication and collaborative debugging by providing extensive insight in pasted code
* fast and easy to use, no ads, no registration

Current Features
-----------
* highlighting with Scala X-Ray
* reusing sbt instances for fast compilation

Plans
----------
* building with sbt, with support for inline dependency specification
* running in sandbox with sbt
* support execution of tests\specs
* linking with sxr-processed libraries
* spawn multiple sbt instances
* allow pastes deletion
* detect fragment pastes and wrap them in top level declaration so that they will be compilable and\or runnable
* detect pastes to gist.github.com pastie.net pastebin.com etc with multibot and copy to scastie
* link stacktraces and compilation errors to source
* optionally load linked sources side-by-side with paste
* evaluate pastes with no dependencies in multibot
* interface for uploading pre-sxred libraries
* generate seo friendly names from paste content
