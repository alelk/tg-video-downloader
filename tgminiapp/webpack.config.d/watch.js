/*
 * Workaround for KT-80582: excessive file watching causes high CPU usage.
 * https://youtrack.jetbrains.com/issue/KT-80582
 *
 * - Ignores .kt files and node_modules from webpack file watcher.
 * - Disables watching on static directories served by devServer.
 *
 * Safe to remove once KT-80582 is fixed in the Kotlin version used by this project.
 */

config.watchOptions = config.watchOptions || {};
config.watchOptions.ignored = config.watchOptions.ignored || ['**/*.kt', '**/node_modules'];

if (config.devServer && config.devServer.static) {
  config.devServer.static = config.devServer.static.map(function (entry) {
    if (typeof entry === 'string') {
      return { directory: entry, watch: false };
    }
    return entry;
  });
}

