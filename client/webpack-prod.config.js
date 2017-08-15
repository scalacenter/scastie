const Path = require('path');
const Merge = require("webpack-merge");

const commonConfig = require('./webpack.common.js');

const CleanWebpackPlugin = require('clean-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const extractSass = new ExtractTextPlugin({
  filename: "[name].[contenthash].css"
});

const publicFolderName = "out/public"

module.exports = Merge(commonConfig, {
  output: {
    path: Path.resolve(__dirname, publicFolderName),
    publicPath: '/public/'
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
