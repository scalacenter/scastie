require("./sass/embed-main.scss");
var common = require("./prod-common.js");

window.ScastieSettings = {
  defaultServerUrl: "https://scastie.scala-lang.org"
};

module.exports = {
  scastie: common.scastie
};