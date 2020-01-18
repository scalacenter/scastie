const Path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const Webpack = require('webpack');
const Merge = require("webpack-merge");

const Common = require('./webpack.common.js');
const devDir = Path.resolve(Common.rootDir, 'dev-static');

const ScalaJs = Merge(Common.ScalaJs, {
  output: {
    publicPath: '/'
  }
});

const Web = Merge(Common.Web, {
  output: {
    publicPath: '/'
  },
  entry: {
    app: Path.resolve(Common.resourcesDir, './dev.js'),
    embedded: Path.resolve(Common.resourcesDir, './dev-embed.js')
  },
  module: {
    rules: [
      {
        test: /\.scss$/,
        use: [
          "style-loader",
          "css-loader",
          "resolve-url-loader",
          "sass-loader?sourceMap"
        ]
      }
    ]
  },
  devServer: {
    hot: true,
    host: "0.0.0.0",
    contentBase: [
      devDir,
      __dirname,
      Common.rootDir
    ],
    proxy: {
      "/***": {
        target: "http://localhost:9000",
        bypass: function(req, res, proxyOptions) {
          // regex matching snippet ids
          var snippet = /(\/[A-Za-z0-9]{22}|\/[A-Za-z0-9]{22}\/([A-Za-z0-9])*[/(0-9)*])/;
          var snippetOld = /(\/[0-9]+)/;
          var backendUrls = /(\/api\/|\/login|\/logout)/;

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
  },
  plugins: [
    new Webpack.HotModuleReplacementPlugin(),
    new HtmlWebpackPlugin({
      filename: "embed.html",
      chunks: ["embedded"],
      template: Path.resolve(Common.resourcesDir, './embed.html'),
      favicon: Path.resolve(Common.resourcesDir, './images/favicon.ico')
    }),
    new HtmlWebpackPlugin({
      filename: "index.html",
      chunks: ["app"],
      template: Path.resolve(Common.resourcesDir, './index.html'),
      favicon: Path.resolve(Common.resourcesDir, './images/favicon.ico')
    })
  ]
});

module.exports = Merge(
  ScalaJs,
  Web
);
