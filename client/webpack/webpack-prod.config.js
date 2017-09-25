const Path = require('path');
const Merge = require("webpack-merge");

const CleanWebpackPlugin = require('clean-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CompressionPlugin = require('compression-webpack-plugin');
const UglifyJSPlugin = require('uglifyjs-webpack-plugin');
const Webpack = require('webpack');

const ProdConfig = 
  new Webpack.DefinePlugin({
    'process.env': {
      NODE_ENV: JSON.stringify('production')
    }
  })

const Common = require('./webpack.common.js');
const publicFolderName = "out/public"

function extract(){
  return new ExtractTextPlugin({
    filename: "[name].css"
  });
}

const extractSassApp = extract();
const extractSassEmbed = extract();

function Web(extractSass){
  return Merge(Common.Web, {
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
              { loader: "css-loader", options: {sourceMap: true} },
              { loader: "resolve-url-loader", options: {sourceMap: true} },
              { loader: "sass-loader", options: {sourceMap: true} }
            ],
            fallback: "style-loader"
          })
        },
        {
          test: /\.js$/,
          use: ["source-map-loader"],
          enforce: "pre"
        }
      ]
    },
    plugins: [
      ProdConfig,
      extractSass,
      new UglifyJSPlugin({
        sourceMap: true
      }),
      new CompressionPlugin({
        asset: "[path].gz[query]",
        algorithm: "gzip",
        test: /\.js$|\.css$|\.html$/,
        threshold: 10240,
        minRatio: 0.8
      })
    ]
  });
}

const WebApp = Merge(Web(extractSassApp), {
  entry: {
    app: Path.resolve(Common.resourcesDir, './prod.js')
  },
  plugins: [
    new HtmlWebpackPlugin({
      filename: "index.html",
      chunks: ["app"],
      template: Path.resolve(Common.resourcesDir, './prod.html'),
      favicon: Path.resolve(Common.resourcesDir, './images/favicon.ico')
    }),
    new CleanWebpackPlugin([publicFolderName], {verbose: false}),
  ]
});

const WebEmbed = Merge(Web(extractSassEmbed), {
  entry: {
    embedded: Path.resolve(Common.resourcesDir, './prod-embed.js')
  }
});

const ScalaJs = Merge(Common.ScalaJs,{
  plugins: [
    ProdConfig
  ]
});


module.exports = [
  ScalaJs,
  WebApp,
  WebEmbed
]
