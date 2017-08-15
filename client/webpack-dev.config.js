const Webpack = require('webpack');
const Merge = require("webpack-merge");

const commonConfig = require('./webpack.common.js');

module.exports = Merge(commonConfig, {
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
    proxy: [{
      context: ["/login", "/logout", "callback", "/api"],
      target: "http://localhost:9000",
    }]
  },
  plugins: [
    new Webpack.HotModuleReplacementPlugin()
  ]
});
