set SCRIPT_DIR=%~dp0
set SBT_OPTS=-agentlib:jdwp=transport=dt_shmem,server=n,address=scastie-play-debug,suspend=y -Dsbt.log.noformat=true
bash "%SCRIPT_DIR%/xsbt.sh" "run -Dlogger.resource=logback.xml"