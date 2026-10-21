module.exports = function (config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    reporters: ['dots'],
    browsers: ['ChromeHeadless'],
    customLaunchers: {
      // CI containers have their own isolation; local tests keep Chrome's sandbox.
      ChromeHeadlessCI: { base: 'ChromeHeadless', flags: ['--no-sandbox', '--disable-dev-shm-usage'] },
    },
    coverageReporter: {
      dir: require('node:path').join(__dirname, 'coverage'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'lcovonly' }, { type: 'text-summary' }],
    },
    // In the 8000-8100 range the EC2 box opens; KARMA_PORT picks another if 8090 is taken
    port: Number(process.env.KARMA_PORT) || 8090,
    singleRun: false,
  });
};
