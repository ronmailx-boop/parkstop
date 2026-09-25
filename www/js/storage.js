// Settings persistence via @capacitor/preferences (Android SharedPreferences).
// Falls back to localStorage when Preferences plugin isn't present (browser preview).

(function (global) {
  const prefs = global.Capacitor && global.Capacitor.Plugins
    ? global.Capacitor.Plugins.Preferences
    : null;

  const SETTINGS_KEY = 'parkstop_settings';

  const DEFAULT_SETTINGS = {
    onboardingComplete: false,
    useBluetooth: true,
    useGeofence: true,
    geofenceRadiusMeters: 50,
    geofenceBackupWindowMinutes: 10,
    carDevice: null, // { name, address }
    parkingApps: [
      { id: 'pango', name: 'Pango', packageName: 'com.pangoinc.pango', deepLink: 'pango://' },
      { id: 'cellopark', name: 'Cellopark', packageName: 'il.co.cellopark', deepLink: 'cellopark://' },
      { id: 'easypark', name: 'EasyPark', packageName: 'com.easypark.android', deepLink: 'easypark://' },
    ],
    selectedParkingAppId: 'pango',
    customParkingAppName: '',
    samsungBatteryChecklistDone: false,
  };

  async function get() {
    if (prefs) {
      const { value } = await prefs.get({ key: SETTINGS_KEY });
      if (!value) return { ...DEFAULT_SETTINGS };
      try {
        return { ...DEFAULT_SETTINGS, ...JSON.parse(value) };
      } catch (e) {
        return { ...DEFAULT_SETTINGS };
      }
    }
    try {
      const raw = localStorage.getItem(SETTINGS_KEY);
      return raw ? { ...DEFAULT_SETTINGS, ...JSON.parse(raw) } : { ...DEFAULT_SETTINGS };
    } catch (e) {
      return { ...DEFAULT_SETTINGS };
    }
  }

  async function set(settings) {
    const value = JSON.stringify(settings);
    if (prefs) {
      await prefs.set({ key: SETTINGS_KEY, value });
    } else {
      localStorage.setItem(SETTINGS_KEY, value);
    }
  }

  async function update(partial) {
    const current = await get();
    const next = { ...current, ...partial };
    await set(next);
    return next;
  }

  global.ParkStopStorage = { get, set, update, DEFAULT_SETTINGS };
})(window);
