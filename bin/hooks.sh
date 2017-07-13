#!/usr/bin/env bash

set -x

HERE="`dirname $0`"

DEST=$HERE/../.git/hooks/pre-commit

if [ ! -f $DEST ]; then
  cp $HERE/pre-commit $DEST
fi
