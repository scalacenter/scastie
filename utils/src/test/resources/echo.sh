#!/usr/bin/env bash

while read -rs -n 4 s; do
  if [[ $s == "quit" ]]; then
    break
  else
    printf $s\\n
  fi
done
