const Path = require('path');
const Webpack = require('webpack');
const Merge = require("webpack-merge");
const CleanWebpackPlugin = require('clean-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

const commonConfig = require('./webpack.common.js');

const extractSass = new ExtractTextPlugin({
  filename: "[name].[contenthash].css"
});

const publicFolderName = "out/public"

const scalaJsConfig = require('./scalajs.webpack.config');
const scalaJsEntry = scalaJsConfig.entry;
const scalaJs = scalaJsEntry["client-opt"];

module.exports = Merge(common.webpackConfig, {
  entry: {
    app: Path.resolve(common.resourcesDir, './prod.js')
  },
  output: {
    path: Path.resolve(__dirname, publicFolderName),
    publicPath: '/public/'
  },
  resolve: {
    alias: {
      'scalajs': scalaJs[0],
    }
  },
  module: {
    rules: [
      {
        test: /\.scss$/,
         use: extractSass.extract({
          use: [
            { loader: "css-loader" },
            { loader: "resolve-url-loader"},
            { loader: "sass-loader?sourceMap" }
          ],
          fallback: "style-loader"
        })
      }
    ]
  },
  plugins: [
    new CleanWebpackPlugin([publicFolderName], {verbose: false}),
    extractSass
  ]
});
