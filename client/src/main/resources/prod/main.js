import { scastie } from '@linkOutputDir/main.js'
import '@resources/sass/app-main.scss'

import * as Sentry from "@sentry/browser";
import { BrowserTracing } from "@sentry/tracing";

Sentry.init({
  dsn: "https://729713d6e2f243a4ae0b16c770e6c071@o1427772.ingest.sentry.io/6778768",
  integrations: [new BrowserTracing()],

  tracesSampleRate: 1.0,
});


window.ScastieSettings = {
  defaultServerUrl: "https://scastie.scala-lang.org"
};

scastie.ScastieMain.main();
