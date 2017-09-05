import common from "./dev-common.js"

common.load(function(){
  setTimeout(function(){
    $e.scastie.ClientMain.main();
  }, 0);
});