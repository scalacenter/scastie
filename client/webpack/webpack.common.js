const Path = require('path');
const Merge = require("webpack-merge");

const generatedConfig = require('./scalajs.webpack.config');
const rootDir = Path.resolve(__dirname, '../../../..');
const resourcesDir = Path.resolve(rootDir, 'src/main/resources');

const Web = {
  devtool: "source-map",
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
  }
}

module.exports = {
  rootDir: rootDir,
  resourcesDir: resourcesDir,
  generatedConfig: generatedConfig,
  Web: Web,
}
