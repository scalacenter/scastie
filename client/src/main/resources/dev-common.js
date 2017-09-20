var defaultServerUrl = "";
var embeddedStylesheetUrl = "/public/embedded.css";

module.exports = {
  com: {
    olegych: {
      scastie: {
        client: {
          ScastieEmbedded: new $e.scastie.ScastieEmbedded(defaultServerUrl, embeddedStylesheetUrl),
          ScastieMain: new $e.scastie.ScastieMain(defaultServerUrl),
          ClientMain: $e.scastie.ClientMain
        }
      }
    }
  }
}