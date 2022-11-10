# Contributing

Take a look at the Backlog in the [Project Page](https://github.com/scalacenter/scastie/projects/1) to see our priorities.
You are more than welcome to contribute any PR regardless if it's listed or not.

## How to run locally

### How to install prerequisites via nix

```
curl -L https://nixos.org/nix/install | sh
nix-shell -v
```

### How to install prerequisites on Mac
```
brew install openjdk@17 sbt nodejs yarn
```

### How to install prerequisites on Windows

Assuming you use Git for Windows >= 2.16.2.1 (note this will erase uncommitted changes):
```
git config --add core.symlinks true
git reset --hard HEAD
```
```
choco install nvm yarn sbt openjdk17 python3
nvm install 8.9.1
nvm use 8.9.1
```
(npm >= 9.0.0 may have issues finding/using python)


### How to run the sbt build

```
sbt
> startAll
```
in a separate terminal, start the vite dev server by running `yarn dev` in the root directory of the repository

open `http://localhost:8080`

then run `~client/fastLinkJS` if you work on client part 
or `~sbtRunner/reStart;server/reStart` if you work on the server part. 

you can also open `http://localhost:8080/embed.html` to edit the embedded style

### Testing production build

Start the sbt production server:

```
sbt
> startAllProd
```

open `http://localhost:9000`

## Scalafmt

Make sure to run `bin/scalafmt` to format your code.

You can install a pre-commit hook with `bin/hooks.sh`

## Structure

```
.
├── api                 | autowire api (rpc server <=> browser)
│                       |   models for server <=> sbt (akka-remote)
├── balancer            | distribute load based on sbt configuration
├── bin                 | scalfmt runner
├── build.sbt           | build definition
├── client              | Scala.js & scalajs-react code for the frontend
├── demo                | cool examples to try in scastie
├── deployment          | production configurations
├── docker              | Dockerfile for sbt images
├── instrumentation     | Worksheet implementation
├── project             | build extras like Deployment or Scala.js packaging and plugins
├── runtime-dotty       | see `runtime-scala`
├── runtime-scala       | methods exposed inside scastie
├── sbt-runner          | remote actor communicating with sbt instance over I/O streams
├── sbt-scastie         | sbt plugin to report errors and console output with the `sbt-api` model
├── metals-runner       | server responsible for managing metals instances to provide interactive features
├── server              | web server
└── utils               | read/writte files
```

## High-Level Architecture


```

 Scala.js Client     run/save/format                                               +-------------------------------------------+
 +---------------------+  AutowireApi      +---------------------+                +-------------------------------------------+|
 |      ScastieBackend |  (HTTP)           | +------------+      | akka+remote   +-------------------------------------------+||
 |          +--------+ +-----------------> | |LoadBalancer| <------------------+ |    SbtActor                Sbt(Proccess)  |||
 |          |        | |                   | +------------+      |             | |   +----------+             +-----------+  |||
 |          |        | |                   |                     |             +---> |          |  <----->    |sbt|scastie|  ||+
 |          |        | |                   |                     |               |   +----------+ I/O Stream  +-----------+  ++
 |          |        | |                   |                     |               +-------------------------------------------+
 |          |        | |                   |                     |
 |          |        | |                   |                     |
 |          |        | |                   |                     |
 |          |        | |                   | SnippetContainer(DB)|
 |          |        | |                   |                     |
 |          |        | | <-----------------+ oauth               |
 |          +--------+ |  SnippetProgress  | static ressources   |
 |                     |  (sse/websocket)  +---------------------+
 | InteractiveProvider |
 |          +--------+ |                   +---------------------+
 |          |        | |                   |                     |
 |          |        | |                   |  MetalsRunnerServer |
 |          |        +-------------------> |                     |
 |          |        | |  (HTTP request)   |                     |
 |          +--------+ |                   |                     |
 |                     |                   |                     |
 +---------------------+                   +---------------------+









Editor: http://asciiflow.com/
```

# Let's talk

If you have any questions join us in the [gitter channel](https://gitter.im/scalacenter/scastie)

# How to deploy

## Quick

```
ssh scastie@alaska.epfl.ch
ssh scastie@scastie.scala-lang.org
ssh scastie@scastie-sbt.scala-lang.org
docker login
cd ~/scastie-secrets && git pull #optional
cd ~/scastie && git pull && ~/nix-user-chroot-bin-1.2.2-x86_64-unknown-linux-musl ~/.nix bash -l
nix-shell -v
sbt 
deploy
```

## Check logs
```
ssh scastie@alaska.epfl.ch
ssh scastie@scastie.scala-lang.org
tail -F -n1000 output.log
ssh scastie@scastie-sbt.scala-lang.org
~/log.sh
```

## Full

0. You need access to `scastie@alaska.epfl.ch`, `https://github.com/scalacenter/scastie-secrets`, `http://scala-webapps.epfl.ch:8081` and be a member of `https://hub.docker.com/u/scalacenter/`

These people have access:

* [@heathermiller](https://github.com/heathermiller)
* [@julienrf](https://github.com/julienrf)
* [@jvican](https://github.com/jvican)
* [@olafurpg](https://github.com/olafurpg)
* [@OlegYch](https://github.com/OlegYch)
* [@dimart](https://github.com/dimart)
* [@rorygraves](https://github.com/rorygraves)

0. see `production.conf` file for current production configuration, notably it uses mongodb instead of local file storage 

0. Install docker (note that Windows is not recommended due to networking issues)

0. do `docker login`

0. bump the version in build.sbt

0. tag and commit the new version

0. Run this command locally: `sbt deploy`

0. Make sure everything went well: https://scastie.scala-lang.org

## Restarting

In case anything goes wrong:

```
ssh scastie@alaska.epfl.ch
ssh scastie@scastie.scala-lang.org
ssh scastie@scastie-sbt.scala-lang.org
./sbt.sh
exit
./server.sh
./metalsRunner.sh
```

# Running with docker locally

```
git commit

sbt "sbtRunner/docker"

docker run \
  --network=host \
  -e RUNNER_PORT=5150 \
  -e RUNNER_HOSTNAME=127.0.0.1 \
  -e RUNNER_RECONNECT=false \
  -e RUNNER_PRODUCTION=true \
  scalacenter/scastie-sbt-runner:`git rev-parse --verify HEAD`
```
