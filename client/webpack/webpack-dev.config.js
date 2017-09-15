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
    contentBase: [
      devDir,
      __dirname,
      Common.rootDir
    ],
    proxy: [{
      context: [
        "/login",
        "/logout",
        "/callback",
        "/api"
      ],
      target: "http://localhost:9000",
    }]
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