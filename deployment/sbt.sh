#!/usr/bin/env bash

whoami

# kill all docker instances
docker kill $(docker ps -q)

# Run all instances
for i in `seq 5150 5169`;
do
  echo "Starting Runner: Port $i"
  docker run --network=host -d -e RUNNER_PRODUCTION=1 -e RUNNER_PORT=$i -e RUNNER_HOSTNAME=scalagesrv3.epfl.ch scalacenter/scastie-sbt-runner:0.1.0
done
