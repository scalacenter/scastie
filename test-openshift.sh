#!/bin/bash
path=`pwd`
win_dir=`cygpath -w "${path}" || echo "${path}"`

export OPENSHIFT_APP_NAME=scastie
#export OPENSHIFT_APP_NAME=scastierenderer1
export OPENSHIFT_REPO_DIR=$path/
export OPENSHIFT_INTERNAL_IP=`hostname -i || echo localhost`
if [ -z ${OPENSHIFT_INTERNAL_PORT} ]; then
  export OPENSHIFT_INTERNAL_PORT=8080
fi
export OPENSHIFT_LOG_DIR=$path/openshift_data/
export OPENSHIFT_DATA_DIR=$win_dir/openshift_data/
.openshift/action_hooks/stop
.openshift/action_hooks/start