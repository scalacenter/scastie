require("./embed-style.js");

import common from "./dev-common.js"

common.load(function(){
  const Scastie = window.exports.scastie.ClientMain

  // Scastie.embedded(
  //   ".scala",
  //   {
  //     targetType: "js"
  //   }
  // );
});