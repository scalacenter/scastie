require("./sass/embed-main.scss");
var common = require("./prod-common.js");

// TODO: find a way to refer to embed-main.scss
var styleUrl = "/public/embedded.css"

var Embedded =
  new common.clientOpt.scastie.Embedded(
    common.defaultServerUrl, 
    styleUrl
  ) 

module.exports = {
  scastie: {
    Embedded: Embedded.embedded,
    EmbeddedRessource: Embedded.embeddedRessource
  }
};