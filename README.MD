[![Build Status](http://drone.geirsson.com:8001/api/badges/scalacenter/scastie/status.svg)](http://drone.geirsson.com:8001/scalacenter/scastie) [![Join the chat](https://badges.gitter.im/scalacenter/scastie.svg)](https://gitter.im/scalacenter/scastie)

# Scala pastebin

https://scastie.scala-lang.org

## Goals

*  enhance communication and collaborative debugging by providing
   extensive insight in pasted code
*  fast and easy to use, no ads, no registration

## Current Features

*  reusing sbt instances for fast compilation
*  distributes workload amongst multiple sbt instances (including remote)
*  realtime update of paste compilation/running progress
*  timeout long-running pastes
*  allow specifying scala version

## Why it does it this way, benefits and drawbacks

Scastie relies on existing scala infrastructure to provide close to real world experience inside
a pastebin-like sandbox. This minimizes the effort to create meaningful paste but might require some
prior experience to fully unleash its (and scala) power.

## High-level design and architecture

The application consists of two modules:

-  scastie - standard akka-http web application module
-  sbt-runner - encapsulates pastes processing
-  balancer - split the load across multiple sbt instances

For each paste create/read request Application controller in scastie module creates a message 
and sends it to PastesActor in renderer module. The PastesActor routes messages to a remote 
SbtActor via akka router.

SbtActor(s) interacts with locally running sbt instance via process I\O streams, which is quite 
hacky, but works good enough. If the sbt instance terminates or otherwise encounters an error 
while processing a paste, the actor will be restarted.

Currently the pastes are stored directly on file system on master node (the one which is running 
web application and PastesActor). 

## How to run

```
sbt
> sbtRunner/reStart
> ~server/reStart
```

open http://localhost:8080

## How to deploy

add your ssh key to
scastie@scastie.scala-lang.org
scastie@scastie-sbt.scala-lang.org

`sbt deploy`

## Embed Scastie into your website

see https://scastie.scala-lang.org/embedded-demo
