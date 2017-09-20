require("./sass/embed-main.scss");
var common = require("./prod-common.js");

var ScastieEmbedded =
  new common.clientOpt.scastie.ScastieEmbedded(
    common.defaultServerUrl
  );

common.scastie.Embedded = ScastieEmbedded.embedded;

common.scastie.EmbeddedRessource = ScastieEmbedded.embeddedRessource;

module.exports = {
  scastie: common.scastie
};