#!/bin/bash
path=`pwd`
win_dir=`cygpath -w "${path}" || echo "${path}"`

export OPENSHIFT_REPO_DIR=$path/
export OPENSHIFT_INTERNAL_IP=localhost
export OPENSHIFT_INTERNAL_PORT=8080
export OPENSHIFT_LOG_DIR=$path/target/
export OPENSHIFT_DATA_DIR=$win_dir/openshift_data/
.openshift/action_hooks/stop
.openshift/action_hooks/start