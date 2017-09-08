require("./style.js");
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
        script.async = true;
        script.onload = function(){
          run();
        };
        script.src = "client-fastopt.js";
        d.getElementsByTagName('head')[0].appendChild(script);
    }(document));

    function run(){

      const Main = $e.scastie.ClientMain

      window.com = {
        olegych: {
          scastie: {
            client: {
              ClientMain: {
                signal: function(a, b, c) {
                  return Main.signal(a, b, c);
                },
                error: function(a, b){
                  return Main.error(a, b);
                },
                embedded: function(s, o, u) {
                  return Main.embedded(s, o, defaultServerUrl);
                }
              }
            }
          }
        }
      }

      const defaultServerUrl = "";

      body(defaultServerUrl);
    }
  }
}