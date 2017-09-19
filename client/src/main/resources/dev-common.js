var defaultServerUrl = "";

module.exports = {
  com: {
    olegych: {
      scastie: {
        client: {
          ScastieMain: new $e.scastie.ScastieMain(defaultServerUrl),
          ClientMain: $e.scastie.ClientMain
        }
      }
    }
  }
}