#!/bin/bash
path=`pwd`
win_dir=`cygpath -w "${path}" || echo "${path}"`

export OPENSHIFT_APP_NAME=${OPENSHIFT_APP_NAME:-scastie}
export OPENSHIFT_REPO_DIR=${OPENSHIFT_REPO_DIR:-$path/}
export OPENSHIFT_INTERNAL_IP=${OPENSHIFT_INTERNAL_IP:-0.0.0.0}
export OPENSHIFT_INTERNAL_PORT=${OPENSHIFT_INTERNAL_PORT:-8080}
export OPENSHIFT_LOG_DIR=${OPENSHIFT_LOG_DIR:-$path/openshift_data/}
export OPENSHIFT_DATA_DIR=${OPENSHIFT_DATA_DIR:-$win_dir/openshift_data/}
.openshift/action_hooks/stop
.openshift/action_hooks/start