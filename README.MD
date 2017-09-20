[![Build Status](https://travis-ci.org/scalacenter/scastie.svg?branch=master)](
  https://travis-ci.org/scalacenter/scastie
) [![Join the chat](https://badges.gitter.im/scalacenter/scastie.svg)](
  https://gitter.im/scalacenter/scastie
)

# [Scastie](https://scastie.scala-lang.org)

<a href="https://scastie.scala-lang.org/004L3z36R6e55O5BvBab7Q">
  <img alt="raycasting demo" src="http://scala-lang.org/resources/img/blog/scastie/scastie2.png" style="width: 400px;">
</a>

## What is Scastie?

Scastie is sbt in your browser. You can:

* Use any version of Scala
* Use alternate backends such as Dotty, Scala.js, Scala Native, and Typelevel Scala.
* Use any publicly available library
* Share Scala programs with anybody
* Embed Scastie into your own website or library user guide.

## How does it work?

When a user evaluates their code, the browser sends all its input to our server.
We run your code in an isolated Java Virtual Machine on our servers.
We integrated a Scaladex interface to allow you to search the Scala ecosystem
and include any published Scala library in your project. You don’t need to
remember what the latest version number of a particular library is anymore!

The output is streamed back to the user. A protocol will allow
the client to interpret different events such as compiler errors,
runtime exceptions, instrumentation, console output, etc.

We also support a worksheet mode, which feels much like a worksheet in an IDE.
It lets a user write code as top-level expressions, without having to put
your code inside of a class or object with a main method. Worksheet mode gives you two ways to interleave your results; on the one hand, when an expression
is evaluated, you can see the value and type of the evaluated expression
to the right of the code that you wrote. On the other hand, worksheet mode
also makes it possible to do a kind of literate programming; that is, you
may interleave code and HTML blocks much like in notebook environments
such as iPython notebooks.

## How do I embed Scastie?

You can embed a snippet with two different ways. You can embed a resource that
was previously saved or you can convert code to a snippet.

### Embedded Resources

Any saved resource can be embedded via a script tag by appending ".js" to its URL.

```html

<!-- Annonymous Resource -->
<script src="https://scastie.scala-lang.org/xbrvky6fTjysG32zK6kzRQ.js"></script>

<!-- User's Resource -->
<script src="https://scastie.scala-lang.org/MasseGuillaume/CpO2s8v2Q1qGdO3vROYjfg.js"></script>
<script src="https://scastie.scala-lang.org/MasseGuillaume/CpO2s8v2Q1qGdO3vROYjfg/1.js"></script>
```

The embedding will be patched at the location of the script tag.

### Converting Code Into Snippets

It's also possible to convert code into a snippet. The preferred way is to embed
a snippet since it's output is cached. This mode will compile and run the snippet
every time it's accessed.

```html

<pre class="simple">
1 + 1
</pre>

<pre class="scala-js">
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLImageElement

val img = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
img.alt = "such dog" 
img.src = "https://goo.gl/a3Xr41"
img
</pre>

<pre class="full"></pre>

<!-- Embedding Script -->
<script src="https://scastie.scala-lang.org/embedded.js"></script>

<script>
window.addEventListener('load', function() {
  // DOM selector without build options
  scastie.Embedded('.simple');

  // with some options
  scastie.Embedded('.scala-js', { targetType: 'js' });

  // all option
  scastie.Embedded('.full', {
    theme: 'light', // (dark, light)
    code: '1 + 1', // default: DOM node content
    worksheetMode: true,
    sbtConfig: 'libraryDependencies += "org.json4s" %% "json4s-native" % "3.5.2"',
    targetType: 'jvm',  // jvm, dotty, typelevel, js
    scalaVersion: '2.12.3'
  });

  // Setting Scala-Native targetType is not yet supported: https://github.com/scalacenter/scastie/issues/50
  // Setting Scala.js version is not yet supported: https://github.com/scalacenter/scastie/issues/228
});
</script>
```