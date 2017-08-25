[![Build Status](https://travis-ci.org/scalacenter/scastie.svg?branch=master)](
  https://travis-ci.org/scalacenter/scastie
) [![Join the chat](https://badges.gitter.im/scalacenter/scastie.svg)](
  https://gitter.im/scalacenter/scastie
)

# Scastie

https://scastie.scala-lang.org

## Goals

*  enhance communication and collaborative debugging by providing
   extensive insight in code snippets
*  fast and easy to use, no ads, no registration

## Current Features

*  reusing sbt instances for fast compilation
*  distributes workload amongst multiple sbt instances (including remote)
*  realtime update of snippet compilation/running progress
*  timeout long-running snippets
*  allow specifying scala version, target and external libraries

## High-level design and architecture

The application consists of three modules:

- scastie - standard akka-http web application module
- sbt-runner - encapsulates snippets processing
- ensime-runner - autocompletion
- balancer - split the load across multiple sbt instances

For each snippet create/read request Application controller in scastie module creates a message 
and sends it to DispatchActor in renderer module. The DispatchActor routes messages to a remote 
SbtActor via akka router.

SbtActor(s) interacts with locally running sbt instance via process I\O streams, which is quite 
hacky, but works good enough. If the sbt instance terminates or otherwise encounters an error 
while processing a snippet, the actor will be restarted.

Currently the snippet are stored directly on file system on master node (the one which is running 
web application).