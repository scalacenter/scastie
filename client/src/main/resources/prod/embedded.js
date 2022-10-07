import '@resources/sass/embed-main.scss'
import { scastie } from '@linkOutputDir/main.js'

window.ScastieSettings = {
  defaultServerUrl: "https://scastie.scala-lang.org"
};

window.scastie = scastie;
