docker run \
  --network=host \
  -d \
  -e RUNNER_PRODUCTION=true \
  -e RUNNER_PORT=5150 \
  -e SERVER_HOSTNAME=127.0.0.1 \
  -e SERVER_AKKA_PORT=15000 \
  -e RUNNER_HOSTNAME=127.0.0.1 \
  scalacenter/scastie-sbt-runner:9f373af33ffd1d479f777e2e7d7f1b020c1eaf32

docker run \
  --network=host \
  -d \
  -e RUNNER_PRODUCTION=true \
  -e RUNNER_PORT=5151 \
  -e SERVER_HOSTNAME=127.0.0.1 \
  -e SERVER_AKKA_PORT=15000 \
  -e RUNNER_HOSTNAME=127.0.0.1 \
  scalacenter/scastie-sbt-runner:9f373af33ffd1d479f777e2e7d7f1b020c1eaf32

docker ps | grep scastie-sbt-runner | awk '{print $1}' | xargs docker stop
