require("./sass/libs.scss");
require("./sass/main.scss");
const scalajs = require("scalajs");

if (window.location.hostname != "localhost") {
  var Raven = require("node_modules/raven-js");

  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-574683-17', 'auto');
  ga('send', 'pageview');

  Raven.config('https://0b9ff62cbc2344369cab867af776ae07@sentry.io/171717').install();
}

const Main = scalajs.scastie.ClientMain;

if (window.location.search === "") {
  Main.main();
} else {
  var h1 = document.createElement("h1");
  h1.innerText = "Foo Bar Tutorial";

  var pre = document.createElement("pre");
  pre.innerText = "1+1";

  var p1 = document.createElement("p");
  p1.innerText = "some text p1";
  
  var pre2 = document.createElement("pre");
  pre2.innerText = "List(1, 2, 3)";

  var p2 = document.createElement("p");
  p2.innerText = "end of document";

  document.body.appendChild(h1);
  document.body.appendChild(pre);
  document.body.appendChild(p1);
  document.body.appendChild(pre2);
  document.body.appendChild(p2);

  document.body.className = "scastie-embedded-dev"

  Main.embedded("pre");
}

module.exports = {
  com: {
    olegych: {
      scastie: {
        client: {
          ClientMain: {
            signal: function(a, b) {
              return Main.signal(a, b);
            },
            error: function(e){
              return Main.error(e);
            },
            embedded: function(s, o) {
              return Main.embedded(s, o);
            }
          }
        }
      }
    }
  }
}