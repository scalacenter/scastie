require("./common.js");

const scalajs = require("scalajs");
const Main = scalajs.scastie.ClientMain;

var Raven = require("node_modules/raven-js");

(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

ga('create', 'UA-574683-17', 'auto');
ga('send', 'pageview');

Raven.config('https://0b9ff62cbc2344369cab867af776ae07@sentry.io/171717').install();

Main.main();

module.exports = {
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