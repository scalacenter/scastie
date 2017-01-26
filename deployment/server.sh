#!/usr/bin/env bash

whoami

HERE="`dirname $0`"

kill `cat server/RUNNING_PID`
rm -rf server

unzip -d server server.zip
mv server/*/* server/

nohup server/bin/server \
  -DSERVER_HOSTNAME=scastie.scala-lang.org
  -Dconfig.file=production.conf \
  -Dlogback.configurationFile=logback.xml &
