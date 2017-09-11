require("./style.js");

const scalajs = require("scalajs");
const Main = scalajs.scastie.ClientMain;

const defaultServerUrl = "https://scastie.scala-lang.org";

module.exports = {
  defaultServerUrl: defaultServerUrl,
  Main: Main,
  com: {
    olegych: {
      scastie: {
        client: {
          ClientMain: {
            signal: function(a, b, c) {
              return Main.signal(a, b, c);
            },
            error: function(a, b){
              return Main.error(a, b);
            },
            embedded: function(s, o) {
              return Main.embedded(s, o, defaultServerUrl);
            },
            embeddedRessource: function(o) {
              return Main.embeddedRessource(o, defaultServerUrl);
            }
          }
        }
      }
    }
  }
}