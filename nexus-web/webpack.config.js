const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin'); // Import the plugin

module.exports = {
  entry: './src/index.tsx',
  mode: 'production',
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: 'ts-loader',
        exclude: /node_modules/,
      },
      {
        test: /\.css$/i,
        use: ['style-loader', 'css-loader', 'postcss-loader'],
      },
    ],
  },
  resolve: {
    extensions: ['.tsx', '.ts', '.js'],
  },
  output: {
    filename: 'bundle.js',
    // We build into the 'web' subfolder to keep assets clean
    path: path.resolve(__dirname, '../app/src/main/assets/web'),
    publicPath: './',
    clean: true,
  },
  plugins: [
    // This plugin copies your index.html and automatically adds <script src="bundle.js"></script>
    new HtmlWebpackPlugin({
      template: './index.html', // Uses the file in nexus-web root
      filename: 'index.html',   // Puts it in assets/web/index.html
    }),
  ],
  performance: {
    hints: false,
  },
};