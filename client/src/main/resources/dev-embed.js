require("./sass/embed-main.scss");
var common = require("./dev-common.js");

var ScastieEmbedded =
  new $e.scastie.ScastieEmbedded(
    common.defaultServerUrl
  );

scastie.Embedded = ScastieEmbedded.embedded;
scastie.EmbeddedRessource = ScastieEmbedded.embeddedRessource;

window.scastie = scastie;

