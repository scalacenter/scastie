const Path = require('path');

const rootDir = Path.resolve(__dirname, '../../../..');
const devDir = Path.resolve(rootDir, 'dev-static');

const Webpack = require('webpack');
const Merge = require("webpack-merge");

const common = require('./webpack.common.js');

module.exports = 
  Merge(common.webpackConfig, {
    output: {
      filename: '[name].js'
    },
    entry: {
      app: Path.resolve(common.resourcesDir, './dev.js'),
      embedded: Path.resolve(common.resourcesDir, './dev-embed.js')
    },
    resolve: {
      alias: {
        "scalajsbundler-entry-point": Path.resolve(__dirname, "scalajsbundler-entry-point.js"),
      }
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
        __dirname
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
      new Webpack.HotModuleReplacementPlugin()
    ]
  });
