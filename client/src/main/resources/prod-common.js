require("./app-style.js");

const clientOpt = require("scalajs/client-opt.js");
const defaultServerUrl = "https://scastie.scala-lang.org";

module.exports = {
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