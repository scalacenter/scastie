require("./common.js");

{
  window.require = (function(x0) {
    return window["__scalajsbundler__".concat(x0)]
  });
  window.exports = {}
};

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
  if (window.location.search === "") {
    window.com = {
      olegych: {
        scastie: {
          client: {
            ClientMain: function(){ return $e.scastie.ClientMain }
          }
        }
      }
    }

    $e.scastie.ClientMain.main();
  } else {
    var h1 = document.createElement("h1");
    h1.innerText = "Foo Bar Tutorial";

    var pre = document.createElement("pre");
    pre.innerText = "1+1";
    pre.className = "dotty";

    var p1 = document.createElement("p");
    p1.innerText = "some text p1";
    
    var pre2 = document.createElement("pre");
    // pre2.innerText = "List(1, 2, 3)";
    pre2.className = "scala";

    var p2 = document.createElement("p");
    p2.innerText = "end of document";

    document.body.appendChild(h1);
    document.body.appendChild(pre);
    document.body.appendChild(p1);
    document.body.appendChild(pre2);
    document.body.appendChild(p2);

    document.body.className = "scastie-embedded-dev"

    document.title = "Embedded Demo"

    $.e.scastie.ClientMain.embedded(
      "pre.dotty",
      {
        serverUrl: "http://localhost:9000",
        targetType: "dotty"
      }
    );

    $.e.scastie.ClientMain.embedded(
      "pre.scala",
      {
        serverUrl: "http://localhost:9000"
      }
    );
  }
}