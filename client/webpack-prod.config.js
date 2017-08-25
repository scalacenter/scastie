const Path = require('path');
const Webpack = require('webpack');
const Merge = require("webpack-merge");
const CleanWebpackPlugin = require('clean-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

const common = require('./webpack.common.js');

const extractSass = new ExtractTextPlugin({
  filename: "[name].[contenthash].css"
});

const publicFolderName = "out/public"

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
      'scalajs': Path.resolve(__dirname, "client-opt.js"),
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
