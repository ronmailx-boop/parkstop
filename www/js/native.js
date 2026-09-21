// Thin wrapper around the native "ParkStopEngine" Capacitor plugin.
// Falls back to harmless no-ops when running outside the native shell
// (e.g. previewing www/ in a desktop browser) so the UI never crashes.

(function (global) {
  const nativePlugin = global.Capacitor && global.Capacitor.Plugins
    ? global.Capacitor.Plugins.ParkStopEngine
    : null;

  const isNative = !!nativePlugin;

  function unavailable(method) {
    console.warn(`[ParkStop] native plugin unavailable, ignoring ${method}()`);
    return Promise.resolve({});
  }

  const ParkStop = {
    isNative,

    getPairedDevices() {
      return nativePlugin ? nativePlugin.getPairedDevices() : Promise.resolve({ devices: [] });
    },

    startMonitoring(options) {
      return nativePlugin ? nativePlugin.startMonitoring(options) : unavailable('startMonitoring');
    },

    stopMonitoring() {
      return nativePlugin ? nativePlugin.stopMonitoring() : unavailable('stopMonitoring');
    },

    snoozeAlert(minutes) {
      return nativePlugin ? nativePlugin.snoozeAlert({ minutes }) : unavailable('snoozeAlert');
    },

    getStatus() {
      return nativePlugin
        ? nativePlugin.getStatus()
        : Promise.resolve({
            state: 'IDLE',
            bluetoothConnected: false,
            lastDistanceMeters: null,
            lastLocationAt: null,
          });
    },

    getLogs() {
      return nativePlugin ? nativePlugin.getLogs() : Promise.resolve({ entries: [] });
    },

    clearLogs() {
      return nativePlugin ? nativePlugin.clearLogs() : unavailable('clearLogs');
    },

    checkPermissions() {
      return nativePlugin ? nativePlugin.checkPermissions() : Promise.resolve({});
    },

    requestPermissions() {
      return nativePlugin ? nativePlugin.requestPermissions() : Promise.resolve({});
    },

    isIgnoringBatteryOptimizations() {
      return nativePlugin
        ? nativePlugin.isIgnoringBatteryOptimizations()
        : Promise.resolve({ ignoring: false });
    },

    openBatteryOptimizationSettings() {
      return nativePlugin ? nativePlugin.openBatteryOptimizationSettings() : unavailable('openBatteryOptimizationSettings');
    },

    openAppDeepLink(options) {
      return nativePlugin ? nativePlugin.openAppDeepLink(options) : unavailable('openAppDeepLink');
    },

    addListener(eventName, callback) {
      if (!nativePlugin) return { remove: () => {} };
      return nativePlugin.addListener(eventName, callback);
    },
  };

  global.ParkStopNative = ParkStop;
})(window);
