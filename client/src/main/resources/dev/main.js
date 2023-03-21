import { scastie } from 'scalajs:main.js'
import '@resources/sass/app-main.scss'

window.ScastieSettings = {
  defaultServerUrl: ""
};

window.scastie = scastie;
scastie.ScastieMain.main();
