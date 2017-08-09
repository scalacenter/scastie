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

ENV SBT_OPTS -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled

210
211
212
213
js 212
dotty


RUN mkdir scala-212
RUN mkdir scala-212/project
RUN echo 'sbt.version = 0.13.15' > scala-212/project/build.properties
RUN echo 'scalaVersion := "2.12.3"' > scala-212/build.sbt
RUN mkdir scala-212/src
RUN mkdir scala-212/src/main
RUN mkdir scala-212/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > scala-212/src/main/scala/Main.scala
RUN cd scala-212; sbt run
RUN cd scala-212; sbt ensimeConfig

RUN mkdir scala-211
RUN mkdir scala-211/project
RUN echo 'sbt.version = 0.13.15' > scala-211/project/build.properties
RUN echo 'scalaVersion := "2.11.11"' > scala-211/build.sbt

RUN echo 'libraryDependencies += "com.typesafe.play" %%% "play-json" % "2.6.2"' >> scala-211/build.sbt
RUN echo '  ,' >> scalajs-212/build.sbt

RUN mkdir scala-211/src
RUN mkdir scala-211/src/main
RUN mkdir scala-211/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > scala-211/src/main/scala/Main.scala
RUN cd scala-211; sbt run
RUN cd scala-211; sbt ensimeConfig

RUN mkdir scalajs-212
RUN mkdir scalajs-212/project
RUN echo 'sbt.version = 0.13.15' > scalajs-212/project/build.properties
# See ScalaTarget defaultScalaJsVersion/defaultScalaVersion for Scala.js/Scala version
RUN echo 'addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.19")' > scalajs-212/project/plugins.sbt
RUN echo 'scalaVersion := "2.12.3"' > scalajs-212/build.sbt
RUN echo 'libraryDependencies ++= Seq(' >> scalajs-212/build.sbt
RUN echo '  "com.typesafe.play" %%% "play-json" % "2.6.2",' >> scalajs-212/build.sbt
RUN echo '  "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion' >> scalajs-212/build.sbt
RUN echo ')' >> scalajs-212/build.sbt
RUN mkdir scalajs-212/src
RUN mkdir scalajs-212/src/main
RUN mkdir scalajs-212/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > scalajs-212/src/main/scala/Main.scala
RUN cd scalajs-212; sbt run

RUN mkdir scala-213
RUN mkdir scala-213/project
RUN echo 'sbt.version = 0.13.15' > scala-213/project/build.properties
RUN echo 'scalaVersion := "2.13.0-M1"' > scala-213/build.sbt
RUN mkdir scala-213/src
RUN mkdir scala-213/src/main
RUN mkdir scala-213/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > scala-213/src/main/scala/Main.scala
RUN cd scala-213; sbt run

RUN mkdir dotty
RUN mkdir dotty/project
RUN echo 'sbt.version = 0.13.15' > dotty/project/build.properties
RUN echo 'addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.3")' > dotty/project/plugins.sbt
RUN echo 'scalaVersion := "0.2.0-RC1"' > dotty/build.sbt
RUN mkdir dotty/src
RUN mkdir dotty/src/main
RUN mkdir dotty/src/main/scala
RUN echo 'object Main { def main(args: Array[String]): Unit = println("Hello, World!") }' > dotty/src/main/scala/Main.scala
RUN cd dotty; sbt run

WORKDIR /home/scastie