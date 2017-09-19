var clientOpt = require("scalajs/client-opt.js");
var defaultServerUrl = "https://scastie.scala-lang.org";

module.exports = {
  defaultServerUrl: defaultServerUrl,  
  clientOpt: clientOpt,
  com: {
    olegych: {
      scastie: {
        client: {
          ScastieMain: new clientOpt.scastie.ScastieMain(defaultServerUrl),
          ClientMain: clientOpt.scastie.ClientMain
        }
      }
    }
  }
}