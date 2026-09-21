(function () {
  const { showToast } = ParkStopCommon;

  const els = {
    permissionStatus: document.getElementById('permission-status'),
    requestPermissionsBtn: document.getElementById('request-permissions-btn'),
    batterySettingsBtn: document.getElementById('battery-settings-btn'),
    batteryStatus: document.getElementById('battery-status'),
    finishBtn: document.getElementById('finish-btn'),
  };

  function describePermissions(perms) {
    const labels = {
      location: 'מיקום',
      backgroundLocation: 'מיקום ברקע',
      bluetooth: 'בלוטות׳',
      notifications: 'התראות',
    };
    const lines = Object.keys(labels).map((key) => {
      const granted = perms && perms[key] === 'granted';
      return `${granted ? '✅' : '⭕'} ${labels[key]}`;
    });
    return lines.join(' · ');
  }

  async function refreshPermissionStatus() {
    const perms = await ParkStopNative.checkPermissions();
    els.permissionStatus.textContent = describePermissions(perms);
  }

  async function refreshBatteryStatus() {
    const { ignoring } = await ParkStopNative.isIgnoringBatteryOptimizations();
    els.batteryStatus.textContent = ignoring
      ? '✅ חיסכון בסוללה כבוי עבור ParkStop'
      : '⚠️ חיסכון בסוללה עדיין פעיל — יש להשלים את השלב הזה';
  }

  function bindEvents() {
    els.requestPermissionsBtn.addEventListener('click', async () => {
      await ParkStopNative.requestPermissions();
      await refreshPermissionStatus();
    });

    els.batterySettingsBtn.addEventListener('click', async () => {
      await ParkStopNative.openBatteryOptimizationSettings();
      setTimeout(refreshBatteryStatus, 1500);
    });

    els.finishBtn.addEventListener('click', async () => {
      await ParkStopStorage.update({ onboardingComplete: true });
      showToast('ההגדרה הושלמה');
      window.location.href = 'index.html';
    });

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        refreshPermissionStatus();
        refreshBatteryStatus();
      }
    });
  }

  async function init() {
    bindEvents();
    await refreshPermissionStatus();
    await refreshBatteryStatus();
  }

  init();
})();
