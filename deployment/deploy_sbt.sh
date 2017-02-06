# Sbt Instances
pushd ..
sbt sbtRunner/dockerBuildAndPush
popd

scp sbt.sh scastie@scastie-sbt.scala-lang.org:sbt.sh
ssh scastie@scastie-sbt.scala-lang.org ./sbt.sh
