var clientOpt = require("scalajs/client-opt.js");
var defaultServerUrl = "https://scastie.scala-lang.org";
var embeddedStylesheetUrl = "/public/embedded.css"

module.exports = {
  defaultServerUrl: defaultServerUrl,  
  clientOpt: clientOpt,
  com: {
    olegych: {
      scastie: {
        client: {
          ScastieEmbedded: new clientOpt.scastie.ScastieEmbedded(defaultServerUrl, embeddedStylesheetUrl),
          ScastieMain: new clientOpt.scastie.ScastieMain(defaultServerUrl),
          ClientMain: clientOpt.scastie.ClientMain
        }
      }
    }
  }
}