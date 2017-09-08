import common from "./dev-common.js"

common.load(function(defaultServerUrl){
  setTimeout(function(){
    $e.scastie.ClientMain.main(defaultServerUrl);
  }, 0);
});