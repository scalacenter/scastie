#!/usr/bin/env bash

# frontend: ssh scastie@scastie.scala-lang.org
# sbt(s):   ssh scastie@scalagesrv3.epfl.ch

HERE="`dirname $0`"

# Sbt Instances
scp sbt.sh scastie@scalagesrv3.epfl.ch:sbt.sh
ssh scastie@scalagesrv3.epfl.ch ./sbt.sh

# Server
scp $HERE/../scastie/target/universal/server-0.1.0.zip scastie@scastie.scala-lang.org:server.zip
scp server.sh scastie@scastie.scala-lang.org:server.sh
scp production.conf scastie@scastie.scala-lang.org:production.conf
scp logback.xml scastie@scastie.scala-lang.org:logback.xml
ssh scastie@scastie.scala-lang.org ./server.sh
