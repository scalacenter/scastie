#!/usr/bin/env bash
set -e
set -o pipefail

echo "[0/7] Deploying SBT runners"
echo "[1/7] Removing old previous image"
docker rmi scalacenter/scastie-sbt-runner:previous

echo "[2/7] Tagging running image as the new previous"
docker tag scalacenter/scastie-sbt-runner:latest scalacenter/scastie-sbt-runner:previous

echo "[3/7] Fetching new latest from repository"
docker pull scalacenter/scastie-sbt-runner:latest

echo "[4/7] Stopping scastie-sbt-runner containers"
docker ps -q --filter "ancestor=scalacenter/scastie-sbt-runner:latest" | xargs docker stop

echo "[5/7] Removing scastie-sbt-runner containers"
docker ps -a -q --filter "status=exited" --filter "ancestor=scalacenter/scastie-sbt-runner:latest" | xargs docker rm

echo "[6/7] Starting SBT runner docker containers"

./start-runners.sh

echo "######################################################"

echo "Docker images:"
docker images

echo "Docker containers:"

docker ps -a

echo "######################################################"


echo "[7/7] Scastie SBT runners deployed successfully"

exit
