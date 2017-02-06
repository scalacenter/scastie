#!/usr/bin/env bash

# frontend: ssh scastie@scastie.scala-lang.org
# sbt(s):   ssh scastie@scastie-sbt.scala-lang.org


./deploy_sbt.sh
./deploy_server.sh

