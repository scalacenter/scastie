const Path = require('path');
const Merge = require("webpack-merge");

const CleanWebpackPlugin = require('clean-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const Common = require('./webpack.common.js');
const publicFolderName = "out/public"

const extractSass = new ExtractTextPlugin({
  filename: "[name].css"
});

const Web = Merge(Common.Web, {
  entry: {
    app: Path.resolve(Common.resourcesDir, './prod.js'),
    embedded: Path.resolve(Common.resourcesDir, './prod-embed.js')
  },
  output: {
    filename: '[name].js',
    path: Path.resolve(__dirname, publicFolderName),
    publicPath: '/public/',
    libraryTarget: 'window'
  },
  resolve: {
    alias: {
      'scalajs': Path.resolve(__dirname)
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
    new HtmlWebpackPlugin({
      filename: "index.html",
      chunks: ["app"],
      template: Path.resolve(Common.resourcesDir, './prod.html'),
      favicon: Path.resolve(Common.resourcesDir, './images/favicon.ico')
    }),
    new CleanWebpackPlugin([publicFolderName], {verbose: false}),
    extractSass
  ]
});

module.exports = [
  Common.ScalaJs,
  Web
]
