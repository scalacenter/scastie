# Contributing

Take a look at the Backlog in the [Project Page](https://github.com/scalacenter/scastie/projects/1) to see our priorities.
You are more than welcome to contribute any PR regardless if it's listed or not.

## How to run locally

### How to install prerequisites via nix

```shell
curl https://nixos.org/nix/install | sh
nix-shell -A scastie
```

### How to install prerequisites on Mac
```shell
brew install openjdk sbt nodejs yarn
```

### How to install prerequisites on Windows

Assuming you use Git for Windows >= 2.16.2.1 (note this will erase uncommitted changes):
```shell
git config --add core.symlinks true
git reset --hard HEAD
```
```shell
choco install nvm yarn sbt jdk8 python3
nvm install 8.9.1
nvm use 8.9.1
```
(npm >= 9.0.0 may have issues finding/using python)


### How to run the sbt build

```
sbt
> startAll
```

open `http://localhost:8080`

then run `~client/fastOptJS` if you work on client part or `~sbtRunner/reStart;server/reStart` if you work on server part. 

you can also open `http://localhost:8080/embed.html` to edit the embedded style

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
├── server              | web server
└── utils               | read/writte files
```

## High-Level Architecture


```
Scala.js Client     run/save/format                                           +-------------------------------------------+
+-----------------+  AutowireApi      +---------------------+                +-------------------------------------------+|
|  ScastieBackend |  (HTTP)           | +------------+      | akka+remote   +-------------------------------------------+||
|      +--------+ +-----------------> | |LoadBalancer| <------------------+ |    SbtActor                Sbt(Proccess)  |||
|      |        | |                   | +------------+      |             | |   +----------+             +-----------+  |||
|      |        | |                   |                     |             +---> |          |  <----->    |sbt|scastie|  ||+
|      |        | |                   |                     |               |   +----+-----+ I/O Stream  +-----------+  |+
|      |        | |                   |                     |               +-------------------------------------------+
|      |        | |                   |                     |
|      |        | |                   |                     |
|      |        | |                   |                     |
|      |        | |                   | SnippetContainer(DB)|
|      |        | |                   |                     |
|      |        | | <-----------------+ oauth               |
|      +--------+ |  SnippetProgress  | static ressources   |
+-----------------+  (sse/websocket)  +---------------------+


Editor: http://asciiflow.com/
```

# Let's talk

If you have any questions join us in the [gitter channel](https://gitter.im/scalacenter/scastie)

# How to deploy

## Quick

```shell
ssh scastie@alaska.epfl.ch
ssh scastie@scastie.scala-lang.org
ssh scastie@scastie-sbt.scala-lang.org
docker login
cd ~/scastie-secrets && git pull #optional
cd ~/scastie && git pull && ~/proot_5.1.1_x86_64_rc2--no-seccomp  -b ~/.nix:/nix
bash 
. /home/scastie/.nix-profile/etc/profile.d/nix.sh
nix-shell -A scastie
sbt 
deploy
```

## Check logs
```shell
ssh scastie@alaska.epfl.ch
ssh scastie@scastie.scala-lang.org
docker logs -f scastie-server
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

```shell
ssh scastie@alaska.epfl.ch
ssh scastie@scastie.scala-lang.org
ssh scastie@scastie-sbt.scala-lang.org
./sbt.sh
exit
./server.sh
```

# Run/test `deploy` task on development machine using vagrant & virtualbox
See guide in [Vagrantfile]

# Running with docker locally
There are 2 options:
1. Using [docker-compose](https://docs.docker.com/compose/install/)
```shell
sbt dockerCompose
```

`dockerCompose` task will build scastie docker images and create `docker-compose.yml`
and run `docker-compose down;docker-compose up`.
See `dockerCompose` alias defined in `build.sbt` for more info.

2. Let `sbt` run `docker` commands directly instead of using `docker-compose`
```shell
sbt deployLocal
```
`deployLocal` task will build scastie docker images and deploy deployment files into `local` folder
and run the `*.sh` file in that folder.
See `deployLocal` alias defined in `build.sbt` for more info.
