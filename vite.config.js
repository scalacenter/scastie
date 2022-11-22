import { spawnSync } from "child_process";
import path from "path";
import { defineConfig } from "vite";

function isDev() {
  return process.env.NODE_ENV !== "production";
}

function emitEmbedded() {
  return process.env.MODE == "embed"
}

function printSbtTask(task) {
  const args = ["-J-Xlog:all=error", "--error", "--batch", `print ${task}`];
  const options = {
    stdio: [
      "pipe", // StdIn.
      "pipe", // StdOut.
      "inherit", // StdErr.
    ],
  };
  const result = spawnSync("sbt", args, options);

  if (result.error)
    throw result.error;
  if (result.status !== 0)
    throw new Error(`sbt process failed with exit code ${result.status}`);
  return result.stdout.toString('utf8').trim();
}

const linkOutputDir = isDev()
  ? printSbtTask("fastLinkOutputDir")
  : printSbtTask("fullLinkOutputDir");


const root = path.resolve('client/src/main/resources', (isDev() ? 'dev' : 'prod'))

const embeddedOptions = {
  input: {
    embedded: path.resolve(root, 'embedded.js')
  },
  output: {
    entryFileNames: "[name].js",
    assetFileNames: "assets/[name].[ext]",
    format: "es"
  }
}

const websiteOptions = {
  input: {
    app: path.resolve(root, 'index.html')
  },
  output: {
    entryFileNames: "[name].js",
    assetFileNames: "assets/[name].[ext]"
  }
}

if (!isDev()) {
  console.info(`Production bundling: ${emitEmbedded() ? 'embedded' : 'website' }`)
} else {
  console.info('Developer mode')
}

const proxy = {
  "/metals": {
    target: "http://localhost:8000"
  },
  "/": {
    target: "http://localhost:9000",
    bypass: function(req, res, proxyOptions) {
      // regex matching snippet ids
      const snippet = /(\/[A-Za-z0-9]{22}|\/[A-Za-z0-9]{22}\/([A-Za-z0-9])*[/(0-9)*])/;
      const snippetOld = /(\/[0-9]+)/;
      const backendUrls = /(\/api\/|\/login|\/logout)/;

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
  root: root,
  base: isDev() ? '' : '/public/',
  resolve: {
    alias: [
      {
        find: '@linkOutputDir',
        replacement: linkOutputDir,
      },
      {
        find: '@resources',
        replacement: path.resolve(__dirname, 'client', 'src', 'main', 'resources'),
      }
    ],
  },
  build: {
    outDir: path.resolve(__dirname, 'client', 'dist', 'public'),
    rollupOptions: emitEmbedded() ? embeddedOptions : websiteOptions,
    emptyOutDir: !emitEmbedded(),
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
