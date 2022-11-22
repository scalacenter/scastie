import { scastie } from '@linkOutputDir/main.js'
import '@resources/sass/app-main.scss'

import * as Sentry from "@sentry/browser";

Sentry.init({
  dsn: "https://729713d6e2f243a4ae0b16c770e6c071@o1427772.ingest.sentry.io/6778768",

  tracesSampleRate: 1.0,
});


window.ScastieSettings = {
  defaultServerUrl: "https://scastie.scala-lang.org"
};

window.scastie = scastie;
scastie.ScastieMain.main();
