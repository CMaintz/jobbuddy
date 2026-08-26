// Karma config for CI and local runs.
//
// Angular's implicit default assumes a desktop Chrome; CI has neither a display nor a usable
// sandbox, so a headless launcher with --no-sandbox is defined here and selected by
// `npm run test:ci`. Plain `npm test` keeps the watch-mode browser for local work.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      // Randomised order surfaces ordering dependencies between specs instead of hiding them.
      jasmine: { random: true },
      clearContext: false,
    },
    jasmineHtmlReporter: { suppressAll: true },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage'),
      subdir: '.',
      reporters: [{ type: 'text-summary' }, { type: 'lcovonly' }],
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        // CI containers run as root without user namespaces, where Chrome's sandbox cannot start.
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    restartOnFileChange: true,
  });
};
