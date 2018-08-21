#!/usr/bin/env bash

VERSION=0.26.0
HASH=7eff4af3d10a9c966e1988257f5d00c4600b647f
FULL="$VERSION+$HASH"

unzip server/target/universal/server-$FULL.zip
server-$FULL/bin/server

docker run \
  --network=host \
  -e RUNNER_PORT=5150 \
  -e RUNNER_HOSTNAME=localhost \
  -e RUNNER_RECONNECT=false \
  -e RUNNER_PRODUCTION=true \
  scalacenter/scastie-sbt-runner:$HASH
