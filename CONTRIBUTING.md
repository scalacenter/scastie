# Contributing

We are currently in the V2 Milestone. Take a look at the V2 Column in the [Project Page](https://github.com/scalacenter/scastie/projects/1) to see our priorities.
You are more than welcome to contribute any PR regardeless if it's listed or not.

## How to run locally

requirements: 

* node (for less)
* export SBT_OPTS = "-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled"

```
sbt
> sbtRunner/reStart
> ~server/reStart
```

open http://localhost:9000

## Structure

```
.
├── api                 | autowire api (rpc server <=> browser) & models for server <=> sbt (akka-remote)
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
├── sbt-api             | api for sbt <=> sbt instance communication over I/O streams
├── sbt-runner          | remote actor communicating with sbt instance over I/O streams
├── sbt-scastie         | sbt plugin to report errors and console output with the `sbt-api` model 
├── server              | web server
└── utils               | read/writte files
```


# How to deploy

## Requirements

1. To deploy the application to the productions servers (scastie.scala-lang.org) you will need to have ssh access to the following machines:

* `scastie@scastie.scala-lang.org`
* `scastie@scastie-sbt.scala-lang.org` (inside EPFL's vpn)

Those people have access:

* @MasseGuillaume
* @heathermiller
* @julienrf
* @jvican
* @olafurpg

2. You need sbt, nodejs and docker

3. bump the version in build.sbt

4. tag and commit the new version

5. Run this command locally: `sbt deploy`

# Let's talk

If you have any questions join us in the [gitter channel](https://gitter.im/scalacenter/scastie)
