require("./common.js");

require("scalajsbundler-entry-point");

{
  window.require = (function(x0) {
    return window["__scalajsbundler__".concat(x0)]
  });
  window.exports = {}
};

module.exports = {
  load: function(body){
    (function(d, script) {
        script = d.createElement('script');
        script.type = 'text/javascript';
        script.async = false;
        script.onload = function(){
          run();
        };
        script.src = "client-fastopt.js";
        d.getElementsByTagName('head')[0].appendChild(script);
    }(document));

    function run(){
      window.com = {
        olegych: {
          scastie: {
            client: {
              ClientMain: function(){ return window.exports.scastie.ClientMain }
            }
          }
        }
      }

      body();
    }
  }
}