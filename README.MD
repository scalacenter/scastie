![Github Build Status](https://github.com/scalacenter/scastie/workflows/Build%20Status/badge.svg)
[![Travis Build Status](https://travis-ci.com/scalacenter/scastie.svg?branch=master)](https://travis-ci.com/scalacenter/scastie)

[![Join the chat](https://badges.gitter.im/scalacenter/scastie.svg)](
  https://gitter.im/scalacenter/scastie
)

# [Scastie](https://scastie.scala-lang.org)

<a href="https://scastie.scala-lang.org/OlegYch/fpDs19tOQYiqILyVmkNC5g">
  <img alt="Demo" src="https://raw.githubusercontent.com/scalacenter/scastie/master/demo/demo.png" style="width: 400px;">
</a>

## What is Scastie?

Scastie is sbt in your browser. You can:

* Use any version of Scala
* Use alternate backends such as Scala 3, Scala.js, Scala Native, and Typelevel Scala.
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

#### Via HTML

Any saved resource can be embedded via a script tag by appending ".js" to its URL.

```html

<!-- Annonymous Resource -->
<script src="https://scastie.scala-lang.org/xbrvky6fTjysG32zK6kzRQ.js"></script>

<!-- User's Resource -->
<script src="https://scastie.scala-lang.org/MasseGuillaume/CpO2s8v2Q1qGdO3vROYjfg.js"></script>
<script src="https://scastie.scala-lang.org/MasseGuillaume/CpO2s8v2Q1qGdO3vROYjfg/1.js"></script>
```

The embedding will be patched at the location of the script tag.

It's also possible to adjust the theme via the GET parameter theme. For example:

```html
<script src="https://scastie.scala-lang.org/xbrvky6fTjysG32zK6kzRQ.js?theme=dark"></script>
```

The theme can be `dark` or `light`. The default value is `light`. It's also possible to customize the css.

Take a look at: https://codepen.io/olegych/pen/OJXVYoR

#### Via Javascript

```html

<!-- Embedding Script -->
<div id="resource-1"></div>
<div id="resource-2"></div>
<div id="resource-3"></div>

<script src="https://scastie.scala-lang.org/embedded.js"></script>

<script>
window.addEventListener('load', function() {
  scastie.Embedded('#resource-1', { 
    base64UUID: "CJ8KTL98QAiWvoVUPI3kXg"
  });

  scastie.Embedded('#resource-2', {
    user: "MasseGuillaume",
    base64UUID: "33D4P3ysQCq2em2MRiv5sQ"
  });


  scastie.Embedded('#resource-3', {
    user: "MasseGuillaume",
    base64UUID: "33D4P3ysQCq2em2MRiv5sQ",
    update: 1
  });
});
</script>
```

Take a look at: https://codepen.io/MasseGuillaume/pen/aLpzvW

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
    theme: 'light', // default: 'light'; ('dark' or 'light')
    code: '1 + 1', // default: DOM node content
    isWorksheetMode: true,
    sbtConfig: 'libraryDependencies += "org.json4s" %% "json4s-native" % "3.5.2"',
    targetType: 'jvm',  // jvm, dotty, typelevel, js
    scalaVersion: '2.12.4'
  });

  // Setting Scala-Native targetType is not yet supported: https://github.com/scalacenter/scastie/issues/50
  // Setting Scala.js version is not yet supported: https://github.com/scalacenter/scastie/issues/228
});
</script>
```

Take a look at: https://codepen.io/MasseGuillaume/pen/OxbVzm

# Supports

Scastie is supported by the Scala Center

[![Scala Center Logo](https://i.imgur.com/kvoAElp.jpg)](https://scala.epfl.ch/)

with the help of Browser Stack for end-to-end testing

[![Browser Stack Logo](http://www.diogonunes.com/blog/wp-content/uploads/2016/07/browserstack-logo.png)](https://browserstack.com)