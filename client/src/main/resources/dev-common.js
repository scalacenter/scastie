var defaultServerUrl = "";

module.exports = {
  defaultServerUrl: defaultServerUrl,
  scastie: {
    ScastieMain: new $e.scastie.ScastieMain(defaultServerUrl),
    ClientMain: $e.scastie.ClientMain
  }
}