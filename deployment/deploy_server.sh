#!/usr/bin/env bash

# Server
scp $HERE/../scastie/target/universal/server-0.3.0-SNAPSHOT.zip scastie@scastie.scala-lang.org:server.zip
scp server.sh scastie@scastie.scala-lang.org:server.sh
scp production.conf scastie@scastie.scala-lang.org:production.conf
scp logback.xml scastie@scastie.scala-lang.org:logback.xml
ssh scastie@scastie.scala-lang.org ./server.sh
