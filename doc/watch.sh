#!/usr/bin/env bash

while inotifywait -e close_write messages.dot;
do
  kill `cat run.pid`
  dot -Tpng messages.dot -o out.png 
  nohup feh out.png > /dev/null 2>&1 & echo $! > run.pid
done