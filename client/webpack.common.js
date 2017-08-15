const Path = require('path');
const Merge = require("webpack-merge");

const rootDir = Path.resolve(__dirname, '../../../..');
const resourcesDir = Path.resolve(rootDir, 'src/main/resources');
const scalaJsConfig = require('./scalajs.webpack.config');

const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = Merge(scalaJsConfig, {
  entry: {
    app: Path.resolve(resourcesDir, './app.js')
  },
  output: {
    filename: '[name].[hash].js',
    libraryTarget: 'window'
  },
  resolve: {
    alias: {
      'resources': resourcesDir,
      'node_modules': Path.resolve(__dirname, 'node_modules')
    }
  },
  module: {
    rules: [
      {
        test: /\.(png|svg|woff|woff2|eot|ttf)$/,
        loader: 'file-loader',
        options: {
          name: "[name].[hash].[ext]"
        }
      }
    ]
  },
  plugins: [
    new HtmlWebpackPlugin({
      template: Path.resolve(resourcesDir, './index.html'),
      favicon: Path.resolve(resourcesDir, './images/favicon.ico')
    })
  ]
});