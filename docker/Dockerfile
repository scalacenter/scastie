FROM openjdk:8u111-jdk-alpine

RUN apk update
RUN apk add --update bash
RUN apk add --update ncurses
RUN apk add --update nodejs

ENV COURSIER_CACHE /root/.coursier
ENV SBT_OPTS -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled

RUN wget http://dl.bintray.com/sbt/native-packages/sbt/0.13.13/sbt-0.13.13.tgz -O /tmp/sbt-0.13.13.tgz
RUN mkdir /tmp/sbt
RUN tar -xzvf /tmp/sbt-0.13.13.tgz -C /tmp
RUN mv /tmp/sbt-launcher-packaging-0.13.13/bin/sbt /usr/local/bin
RUN mv /tmp/sbt-launcher-packaging-0.13.13/bin/sbt-launch-lib.bash /usr/local/bin
RUN mv /tmp/sbt-launcher-packaging-0.13.13/bin/sbt-launch.jar /usr/local/bin

RUN mkdir /root/.sbt
RUN mkdir /root/.sbt/0.13
RUN mkdir /root/.sbt/0.13/plugins
RUN echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M14")' >> /root/.sbt/0.13/plugins/plugins.sbt

RUN mkdir /root/scala-212
RUN mkdir /root/scala-212/project
RUN echo 'sbt.version = 0.13.13' > /root/scala-212/project/build.properties
RUN echo 'scalaVersion := "2.12.1"' > /root/scala-212/build.sbt
RUN mkdir /root/scala-212/src
RUN mkdir /root/scala-212/src/main
RUN mkdir /root/scala-212/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /root/scala-212/src/main/scala/Main.scala
RUN cd /root/scala-212; sbt run

RUN mkdir /root/scala-211
RUN mkdir /root/scala-211/project
RUN echo 'sbt.version = 0.13.13' > /root/scala-211/project/build.properties
RUN echo 'scalaVersion := "2.11.8"' > /root/scala-211/build.sbt
RUN mkdir /root/scala-211/src
RUN mkdir /root/scala-211/src/main
RUN mkdir /root/scala-211/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /root/scala-211/src/main/scala/Main.scala
RUN cd /root/scala-211; sbt run

RUN mkdir /root/scala-210
RUN mkdir /root/scala-210/project
RUN echo 'sbt.version = 0.13.13' > /root/scala-210/project/build.properties
RUN echo 'scalaVersion := "2.10.6"' > /root/scala-210/build.sbt
RUN mkdir /root/scala-210/src
RUN mkdir /root/scala-210/src/main
RUN mkdir /root/scala-210/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /root/scala-210/src/main/scala/Main.scala
RUN cd /root/scala-210; sbt run

RUN mkdir /root/dotty
RUN mkdir /root/dotty/project
RUN echo 'sbt.version = 0.13.13' > /root/dotty/project/build.properties
RUN echo 'addSbtPlugin("com.felixmulder" % "sbt-dotty" % "0.1.9")' > /root/dotty/project/plugins.sbt
RUN echo 'enablePlugins(DottyPlugin)' > /root/dotty/build.sbt
RUN mkdir /root/dotty/src
RUN mkdir /root/dotty/src/main
RUN mkdir /root/dotty/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /root/dotty/src/main/scala/Main.scala
RUN cd /root/dotty; sbt run

RUN apk add --update rsync
