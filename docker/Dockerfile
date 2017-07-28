FROM openjdk:8u111-jdk-alpine

RUN apk update
RUN apk add --update bash
RUN apk add --update ncurses
RUN apk add --update nodejs
RUN apk add --update curl
RUN apk add --update graphviz

# JRE fails to load fonts if there are no standard fonts in the image; DejaVu is a good choice,
# see https://github.com/docker-library/openjdk/issues/73#issuecomment-207816707
RUN apk add --update ttf-dejavu
RUN apk add --update font-adobe-100dpi
RUN apk add --update git
RUN apk add --update procps

# These fonts are somewhat prettier
RUN git clone --depth 1 --branch release https://github.com/adobe-fonts/source-code-pro.git /usr/share/fonts/source-code-pro && \
  rm -rf /usr/share/fonts/source-code-pro/.git && \
  fc-cache -f -v /usr/share/fonts/source-code-pro

RUN apk update && apk add --no-cache fontconfig && \
  mkdir -p /usr/share && \
  cd /usr/share \
  && curl -L https://github.com/Overbryd/docker-phantomjs-alpine/releases/download/2.11/phantomjs-alpine-x86_64.tar.bz2 | tar xj \
  && ln -s /usr/share/phantomjs/phantomjs /usr/bin/phantomjs

RUN wget http://dl.bintray.com/sbt/native-packages/sbt/0.13.15/sbt-0.13.15.tgz -O /tmp/sbt-0.13.15.tgz
RUN mkdir -p /app/sbt
RUN tar -xzvf /tmp/sbt-0.13.15.tgz -C /app/sbt
RUN ln -s /app/sbt/sbt/bin/sbt /usr/local/bin/sbt

RUN addgroup -g 433 scastie
RUN adduser -h /home/scastie -G scastie -D -u 433 -s /bin/sh scastie
USER scastie

ENV LANG en_US.UTF-8

ENV COURSIER_CACHE /home/scastie/.coursier
ENV SBT_OPTS -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled

RUN mkdir /home/scastie/.sbt
RUN mkdir /home/scastie/.sbt/0.13
RUN mkdir /home/scastie/.sbt/0.13/plugins

RUN echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC7")' >> /home/scastie/.sbt/0.13/plugins/plugins.sbt
RUN echo 'addSbtPlugin("org.ensime" % "sbt-ensime" % "1.12.13")' >> /home/scastie/.sbt/0.13/plugins/plugins.sbt

RUN mkdir /home/scastie/scala-212
RUN mkdir /home/scastie/scala-212/project
RUN echo 'sbt.version = 0.13.15' > /home/scastie/scala-212/project/build.properties
RUN echo 'scalaVersion := "2.12.3"' > /home/scastie/scala-212/build.sbt
RUN mkdir /home/scastie/scala-212/src
RUN mkdir /home/scastie/scala-212/src/main
RUN mkdir /home/scastie/scala-212/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /home/scastie/scala-212/src/main/scala/Main.scala
RUN cd /home/scastie/scala-212; sbt run
RUN cd /home/scastie/scala-212; sbt ensimeConfig

RUN mkdir /home/scastie/scala-211
RUN mkdir /home/scastie/scala-211/project
RUN echo 'sbt.version = 0.13.15' > /home/scastie/scala-211/project/build.properties
RUN echo 'scalaVersion := "2.11.11"' > /home/scastie/scala-211/build.sbt
RUN mkdir /home/scastie/scala-211/src
RUN mkdir /home/scastie/scala-211/src/main
RUN mkdir /home/scastie/scala-211/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /home/scastie/scala-211/src/main/scala/Main.scala
RUN cd /home/scastie/scala-211; sbt run
RUN cd /home/scastie/scala-211; sbt ensimeConfig

RUN mkdir /home/scastie/scala-213
RUN mkdir /home/scastie/scala-213/project
RUN echo 'sbt.version = 0.13.15' > /home/scastie/scala-213/project/build.properties
RUN echo 'scalaVersion := "2.13.0-M1"' > /home/scastie/scala-213/build.sbt
RUN mkdir /home/scastie/scala-213/src
RUN mkdir /home/scastie/scala-213/src/main
RUN mkdir /home/scastie/scala-213/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /home/scastie/scala-213/src/main/scala/Main.scala
RUN cd /home/scastie/scala-213; sbt run

RUN mkdir /home/scastie/dotty
RUN mkdir /home/scastie/dotty/project
RUN echo 'sbt.version = 0.13.15' > /home/scastie/dotty/project/build.properties
RUN echo 'addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.3")' > /home/scastie/dotty/project/plugins.sbt
RUN echo 'scalaVersion := "0.2.0-RC1"' > /home/scastie/dotty/build.sbt
RUN mkdir /home/scastie/dotty/src
RUN mkdir /home/scastie/dotty/src/main
RUN mkdir /home/scastie/dotty/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /home/scastie/dotty/src/main/scala/Main.scala
RUN cd /home/scastie/dotty; sbt run

RUN mkdir /home/scastie/scalajs-212
RUN mkdir /home/scastie/scalajs-212/project
RUN echo 'sbt.version = 0.13.15' > /home/scastie/scalajs-212/project/build.properties
# See ScalaTarget defaultScalaJsVersion/defaultScalaVersion for Scala.js/Scala version
RUN echo 'addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.18")' > /home/scastie/scalajs-212/project/plugins.sbt
RUN echo 'scalaVersion := "2.12.3"' > /home/scastie/scalajs-212/build.sbt
RUN mkdir /home/scastie/scalajs-212/src
RUN mkdir /home/scastie/scalajs-212/src/main
RUN mkdir /home/scastie/scalajs-212/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > /home/scastie/scalajs-212/src/main/scala/Main.scala
RUN cd /home/scastie/scalajs-212; sbt run

WORKDIR /home/scastie