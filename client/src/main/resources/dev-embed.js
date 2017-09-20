require("./sass/embed-main.scss");
var common = require("./dev-common.js");

// TODO: find a way to refer to embed-main.scss
var embeddedStylesheetUrl = "/public/embedded.css";

common.scastie.ScastieEmbedded =
  new $e.scastie.ScastieEmbedded(
    defaultServerUrl,
    embeddedStylesheetUrl
  );

window.scastie = common.scastie;