# Contributing

Take a look at the Backlog in the [Project Page](https://github.com/scalacenter/scastie/projects/1) to see our priorities.
You are more than welcome to contribute any PR regardless if it's listed or not.

## How to run locally

requirements: 

* node
* phantomjs (for running client tests) ```npm install -g phantomjs```
* sbt
* docker (for deploying)
* sass (http://sass-lang.com/install)

```
sbt
> sbtRunner/reStart
> ~server/reStart
```

open http://localhost:9000

## Scalafmt

Make sure to run `bin/scalafmt` to format your code.

You can install a pre-commit hook with `bin/hooks.sh`

## Scalafix

run `sbt scalafix`

## Structure

```
.
├── api                 | autowire api (rpc server <=> browser)
|                       |   models for server <=> sbt (akka-remote)
├── balancer            | distribute load based on sbt configuration
├── bin                 | scalfmt runner
├── build.sbt           | build definition
├── client              | Scala.js & scalajs-react code for the frontend 
├── codemirror          | Codemirror facade
├── demo                | cool examples to try in scastie
├── deployment          | production configurations
├── docker              | Dockerfile for sbt images
├── instrumentation     | Worksheet implementation
├── project             | build extras like Deployment or Scala.js packaging and plugins
├── runtime-dotty       | see `runtime-scala`
├── runtime-scala       | methods exposed inside scastie
├── sbt-runner          | remote actor communicating with sbt instance over I/O streams
├── sbt-scastie         | sbt plugin to report errors and console output with the `sbt-api` model 
├── server              | web server
└── utils               | read/writte files
```

## High-Level Architecture


```
                                                                            +-------------------------------------------+
                                                                           +-------------------------------------------+|
 Scala.js Client     run/save/format                                      +-------------------------------------------+||
+-----------------+  AutowireApi      +---------------------+             |    SbtActor                Sbt(Proccess)  |||
|  ScastieBackend |  (HTTP)           | +------------+      | akka+remote |   +----------+             +-----------+  |||
|      +--------+ +-----------------> | |LoadBalancer| <--------------------> |          |  <----->    |sbt|scastie|  |||
|      |        | |                   | +------------+      |             |   +----+-----+ I/O Stream  +-----------+  |||
|      |        | |                   |                     |             |        ^                                  |||
|      |        | |                   | SnippetContainer(DB)|             |        |                                  |||
|      |        | |                   |                     |             |        v                                  |||
|      |        | | <-----------------+ oauth               |             |   +----+-----+  <----->    +-----------+  |||
|      +--------+ |  SnippetProgress  | static ressources   |             |   |          |   Jerky     |           |  |||
+-----------------+  (sse/websocket)  +---------------------+             |   +----------+ (websocket) +-----------+  ||+
                                                                          |    EnsimeActor               Ensime       |+
                                                                          +-------------------------------------------+

Editor: http://asciiflow.com/
```

# Let's talk

If you have any questions join us in the [gitter channel](https://gitter.im/scalacenter/scastie)

# How to deploy

## Requirements

1. To deploy the application to the productions servers (scastie.scala-lang.org) you will need to have ssh access to the following machine:

* `scastie@scastie.scala-lang.org`

These people have access:

* [@MasseGuillaume](https://github.com/MasseGuillaume)
* [@heathermiller](https://github.com/heathermiller)
* [@julienrf](https://github.com/julienrf)
* [@jvican](https://github.com/jvican)
* [@olafurpg](https://github.com/olafurpg)
* [@dimart](https://github.com/dimart)

2. You need to be a member of the scalacenter on dockerhub: https://hub.docker.com/u/scalacenter 

3. do`docker login`

4. bump the version in build.sbt

5. tag and commit the new version

6. Run this command locally: `sbt deploy`

7. Make sure everything went well: https://scastie.scala-lang.org

## Restarting

In case anything goes wrong:

```
ssh scastie@scastie.scala-lang.org
ssh scastie@scastie-sbt.scala-lang.org
./sbt.sh
exit
./server.sh
```

# Running with docker locally

```
git commit

sbt sbtRunner/docker

docker run \
  --network=host
  -e RUNNER_PORT=5150 \
  -e RUNNER_HOSTNAME=localhost \
  -e RUNNER_RECONNECT=false \
  -e RUNNER_PRODUCTION=true \
  scalacenter/scastie-sbt-runner:`git rev-parse --verify HEAD`
```