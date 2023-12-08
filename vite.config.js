import { spawnSync } from "child_process";
import path from "path";
import { defineConfig, splitVendorChunkPlugin } from "vite";
import { plugin as mdPlugin } from 'vite-plugin-markdown';
import scalaJSPlugin from '@scala-js/vite-plugin-scalajs';

function isDev() {
  return process.env.NODE_ENV !== "production";
}

function emitEmbedded() {
  return process.env.MODE == "embed"
}

const root = path.resolve('client/src/main/resources', (isDev() ? 'dev' : 'prod'))

const embeddedOptions = {
  input: {
    embedded: path.resolve(root, 'embedded.js')
  },
  output: {
    entryFileNames: "embedded/[name].js",
    assetFileNames: "embedded/[name].[ext]"
  }
}

const embeddedLibrary = {
  entry: path.resolve(root, 'embedded.js'),
  name: "scastie",
  formats: ['umd'],
}

const websiteOptions = {
  input: {
    app: path.resolve(root, 'index.html')
  },
  output: {
    entryFileNames: "[name]-[hash].js",
    assetFileNames: "assets/[name]-[hash].[ext]"
  }
}

if (!isDev()) {
  console.info(`Production bundling: ${emitEmbedded() ? 'embedded' : 'website' }`)
} else {
  console.info('Developer mode')
}

const proxy = {
  "/metals": {
    target: "http://0.0.0.0:8000"
  },
  "/": {
    target: "http://0.0.0.0:9000",
    bypass: function(req, res, proxyOptions) {
      // regex matching snippet ids
      const snippet = /(\/[A-Za-z0-9]{22}|\/[A-Za-z0-9]{22}\/([A-Za-z0-9])*[/(0-9)*])/;
      const snippetOld = /(\/[0-9]+)/;
      const backendUrls = /(\/api\/|\/login|\/logout|\/*.wasm|\/*.scm)/;

      if(!backendUrls.test(req.url)) {
        if (snippet.test(req.url) || snippetOld.test(req.url)) {
          console.log("index: " + req.url);
          return "/";
        } else {
          if (req.url.startsWith("/try")) {
            return "/";
          } else {
            console.log("other: " + req.url);
            return req.url;
          }
        }
      } else {
        console.log("proxied: " + req.url);
      }
    }
  }
}

export default defineConfig({
  define: {
    'process.env.NODE_ENV': `"${process.env.NODE_ENV}"`,
    'process.env.MODE': `"${process.env.MODE}"`
  },
  root: root,
  base: isDev() ? '' : '/public/',
  plugins: [
    scalaJSPlugin({
      projectID: 'client'
    }),
    splitVendorChunkPlugin(),
    mdPlugin({
      mode: ['html']
    }),
  ],
  resolve: {
    alias: [
      {
        find: '@resources',
        replacement: path.resolve(__dirname, 'client', 'src', 'main', 'resources'),
      },
      {
        find: '@scastieRoot',
        replacement: path.resolve(__dirname),
      }
    ],
  },
  build: {
    outDir: path.resolve(__dirname, 'client', 'dist', 'public'),
    rollupOptions: emitEmbedded() ? embeddedOptions : websiteOptions,
    emptyOutDir: !emitEmbedded(),
    // Embedded is used as a library, in order to support current scastie embedded users.
    // It outputs 'umd' module which allows to use <script> tag without specifying its type to "module"
    lib: emitEmbedded() ? embeddedLibrary : null,
  },
  css: {
    devSourcemap: true,
    preprocessorOptions: {
       stylus: { // or stylus, depending on the stylus files extension name you use
         imports: [path.resolve(__dirname, 'node_modules', 'highlight.js', 'styles')],
       }
    }
  },
  server: {
    proxy: proxy,
    port: 8080,
    strictPort: true,
  },
  preview: {
    port: 8080,
    strictPort: true,
  }
});
