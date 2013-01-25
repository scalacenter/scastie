#!/bin/bash
path=`pwd`
win_dir=`cygpath -w "${path}" || echo "${path}"`

if [ -z ${OPENSHIFT_APP_NAME} ]; then
  export OPENSHIFT_APP_NAME=scastie
fi
#export OPENSHIFT_APP_NAME=scastierenderer1
export OPENSHIFT_REPO_DIR=$path/
export OPENSHIFT_INTERNAL_IP=`hostname -i || echo 0.0.0.0`
if [ -z ${OPENSHIFT_INTERNAL_PORT} ]; then
  export OPENSHIFT_INTERNAL_PORT=8080
fi
export OPENSHIFT_LOG_DIR=$path/openshift_data/
export OPENSHIFT_DATA_DIR=$win_dir/openshift_data/
.openshift/action_hooks/stop
.openshift/action_hooks/start