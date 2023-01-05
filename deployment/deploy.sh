#!/usr/bin/env bash
set -e
set -o pipefail

if [[ $# -ne 2 ]]; then
  echo "Script takes 2 arguments"
  exit
fi

if [[ $1 = "Metals" ]]; then
  repository_name="scalacenter/scastie-metals-runner"

  if [[ $2 = "Staging" ]]; then
    container_name_regex="^scastie-metals-runner-staging$"
    script=./start-metals-staging.sh
  elif [[ $2 = "Production" ]]; then
    container_name_regex="^scastie-metals-runner$"
    script=./start-metals.sh
  else
    echo "Illegal argument: $2"
    exit
  fi

elif [[ $1 = "SBT" ]]; then
  repository_name="scalacenter/scastie-sbt-runner"

  if [[ $2 = "Staging" ]]; then
    container_name_regex="^scastie-sbt-runner-staging-\d+$"
    script=./start-runners-staging.sh
  elif [[ $2 = "Production" ]]; then
    container_name_regex="^scastie-sbt-runner-\d+$"
    script=./start-runners.sh
  else
    echo "Illegal argument: $2"
    exit
  fi
else
  echo "Illegal argument: $1"
  exit
fi

if [[ $2 = "Staging" ]]; then
  previous_tag="previous-staging"
elif [[ $2 = "Production" ]]; then
  previous_tag="previous"
else
  echo "Incorrect second argument: $2"
  exit
fi

echo "[0/7] Deploying $1 runners"
echo "[1/7] Removing old previous image: $repository_name:$previous_tag"
docker rmi $repository_name:$previous_tag || true

echo "[2/7] Tagging image $repository_name:latest as the new previous: $repository_name:$previous_tag"
docker tag $repository_name:latest $repository_name:$previous_tag

echo "[3/7] Fetching new latest from repository: $repository_name"
docker pull $repository_name:latest

echo "[4/7] Stopping $repository_name $2 containers"
docker ps -q --filter "name=$container_name_regex" | xargs docker stop || true

echo "[5/7] Removing $repository_name $2 containers"
docker ps -a -q --filter "status=exited" --filter "name=$container_name_regex" | xargs docker rm || true

echo "[6/7] Starting $1 runner docker containers with script $script"

eval $script


echo "######################################################"

echo "Docker images:"
docker images

echo "Docker containers:"

docker ps -a

echo "######################################################"


echo "[7/7] Scastie $1 deployed successfully"

exit
