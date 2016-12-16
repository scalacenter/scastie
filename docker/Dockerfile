# docker build -t scalacenter/scastie-docker-sbt:0.13.13 .
# docker login
# docker push scalacenter/scastie-docker-sbt:0.13.13

FROM openjdk:8u111-jdk-alpine

RUN apk add --update bash
RUN apk add --update ncurses
# RUN apk add --update nano
  
# sbt
RUN wget http://dl.bintray.com/sbt/native-packages/sbt/0.13.13/sbt-0.13.13.tgz -O /tmp/sbt-0.13.13.tgz
RUN mkdir /tmp/sbt
RUN tar -xzvf /tmp/sbt-0.13.13.tgz -C /tmp
RUN mv /tmp/sbt-launcher-packaging-0.13.13/bin/sbt /usr/local/bin
RUN mv /tmp/sbt-launcher-packaging-0.13.13/bin/sbt-launch-lib.bash /usr/local/bin
RUN mv /tmp/sbt-launcher-packaging-0.13.13/bin/sbt-launch.jar /usr/local/bin

# coursier
RUN mkdir /root/.sbt
RUN mkdir /root/.sbt/0.13
RUN mkdir /root/.sbt/0.13/plugins
RUN echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M14")' >> /root/.sbt/0.13/plugins/plugins.sbt

# RUN mkdir /root/sbt-test
# RUN mkdir /root/sbt-test/project
# RUN echo 'sbt.version' > /root/sbt-test/project/build.properties
# RUN echo 'scalaVersion := "2.12.1"' > /root/sbt-test/build.sbt
# RUN mkdir /root/sbt-test/src
# RUN mkdir /root/sbt-test/src/main
# RUN mkdir /root/sbt-test/src/main/scala
# RUN echo 'object Main { def main(args: Array[String]): Unit = { println("Hello, World!") } }' > /root/# sbt-test/src/main/scala/Main.scala
# RUN cd /root/sbt-test; sbt -Dsbt.log.noformat=true -Djline.terminal=jline.UnsupportedTerminal run
