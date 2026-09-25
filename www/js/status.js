(function () {
  const { showToast, stateLabel } = ParkStopCommon;

  const els = {
    checklist: document.getElementById('checklist'),
    samsungCard: document.getElementById('samsung-card'),
    samsungSettingsBtn: document.getElementById('samsung-settings-btn'),
    samsungDoneCheckbox: document.getElementById('samsung-done-checkbox'),
    testAlertNowBtn: document.getElementById('test-alert-now-btn'),
    testAlertDelayedBtn: document.getElementById('test-alert-delayed-btn'),
    simulateBtBtn: document.getElementById('simulate-bt-btn'),
    manualStartBtn: document.getElementById('manual-start-btn'),
  };

  function chip(ok) {
    return ok ? '<span class="chip ok">תקין</span>' : '<span class="chip fail">חסר</span>';
  }

  function row(title, ok, fixLabel, fixHandlerName, hintWhenFail) {
    const fixBtn = !ok && fixLabel
      ? `<button class="btn btn-ghost" data-fix="${fixHandlerName}">${fixLabel}</button>`
      : '';
    const hint = !ok && hintWhenFail ? `<p class="hint">${hintWhenFail}</p>` : '';
    return `<div class="checklist-row">
      <span class="row-title">${title}</span>
      <span class="checklist-actions">${chip(ok)}${fixBtn}</span>
    </div>${hint}`;
  }

  async function buildChecklist() {
    const [serviceStatus, perms, battery, status] = await Promise.all([
      ParkStopNative.isServiceRunning(),
      ParkStopNative.getDetailedPermissionStatus(),
      ParkStopNative.isIgnoringBatteryOptimizations(),
      ParkStopNative.getStatus(),
    ]);

    const isActive = status.state === 'MONITORING' || status.state === 'ALERT';
    const serviceOk = !isActive || serviceStatus.running;
    const carDeviceOk = !!status.carDeviceAddress;

    const rows = [
      row('שירות רקע רץ', serviceOk, isActive ? 'הפעל מחדש' : null, 'restartService'),
      row(
        'מיקום "תמיד" (ברקע)',
        perms.backgroundLocation,
        'פתח הגדרות',
        'openAppSettings',
        'המסך שנפתח הוא פרטי האפליקציה. היכנסו ל״הרשאות״ ‹ ״מיקום״, ובחרו ידנית ' +
          '״אפשר תמיד״ (לא ״רק בזמן שהאפליקציה בשימוש״) — אנדרואיד לא תמיד מציע את זה ' +
          'ישירות בבקשת ההרשאה הרגילה.'
      ),
      row('הרשאת בלוטות׳', perms.bluetooth, 'בקש הרשאה', 'requestPermissions'),
      row('הרשאת התראות', perms.notifications, 'בקש הרשאה', 'requestPermissions'),
      row('פטור מאופטימיזציית סוללה', battery.ignoring, 'פתח הגדרות סוללה', 'openBattery'),
      row('מכשיר רכב הוגדר', carDeviceOk, 'בחר מכשיר', 'openSettings'),
      row('חניה פעילה', isActive, null, null) + `<p class="hint">${stateLabel(status.state)}</p>`,
    ];

    els.checklist.innerHTML = rows.join('');
  }

  const fixHandlers = {
    async restartService() {
      await ParkStopNative.restartServiceIfNeeded();
      showToast('מנסה להפעיל מחדש את השירות');
      setTimeout(buildChecklist, 1500);
    },
    async openAppSettings() {
      await ParkStopNative.openAppSettings();
    },
    async requestPermissions() {
      await ParkStopNative.requestPermissions();
      setTimeout(buildChecklist, 1000);
    },
    async openBattery() {
      await ParkStopNative.openBatteryOptimizationSettings();
      setTimeout(buildChecklist, 1500);
    },
    async openSettings() {
      window.location.href = 'settings.html';
    },
  };

  async function refreshSamsungCard() {
    const { isSamsung } = await ParkStopNative.isSamsungDevice();
    els.samsungCard.style.display = isSamsung ? 'block' : 'none';
    if (!isSamsung) return;

    const settings = await ParkStopStorage.get();
    els.samsungDoneCheckbox.checked = !!settings.samsungBatteryChecklistDone;
  }

  function bindEvents() {
    els.checklist.addEventListener('click', (event) => {
      const btn = event.target.closest('[data-fix]');
      if (!btn) return;
      const handler = fixHandlers[btn.dataset.fix];
      if (handler) handler();
    });

    els.samsungSettingsBtn.addEventListener('click', () => {
      ParkStopNative.openBatteryOptimizationSettings();
    });

    els.samsungDoneCheckbox.addEventListener('change', async () => {
      await ParkStopStorage.update({ samsungBatteryChecklistDone: els.samsungDoneCheckbox.checked });
    });

    els.testAlertNowBtn.addEventListener('click', async () => {
      await ParkStopNative.sendTestAlert();
      showToast('התראת הבדיקה נשלחה');
    });

    els.testAlertDelayedBtn.addEventListener('click', async () => {
      await ParkStopNative.scheduleTestAlert(30);
      showToast('התראת בדיקה תגיע בעוד 30 שניות — אפשר לנעול את המסך');
    });

    els.simulateBtBtn.addEventListener('click', async () => {
      try {
        await ParkStopNative.simulateBluetoothConnect();
        showToast('הדמיית חיבור בלוטות׳ בוצעה');
      } catch (e) {
        showToast('אין חניה פעילה במעקב — אי אפשר להדמות');
      }
    });

    els.manualStartBtn.addEventListener('click', async () => {
      const settings = await ParkStopStorage.get();
      await ParkStopNative.startMonitoring({
        parkingAppId: 'test-manual',
        parkingAppName: 'בדיקה ידנית',
        parkingAppPackage: '',
        parkingAppDeepLink: '',
        useBluetooth: settings.useBluetooth,
        useGeofence: settings.useGeofence,
        carDeviceAddress: settings.carDevice ? settings.carDevice.address : '',
        carDeviceName: settings.carDevice ? settings.carDevice.name : '',
        geofenceRadiusMeters: settings.geofenceRadiusMeters,
        geofenceBackupWindowMinutes: settings.geofenceBackupWindowMinutes,
      });
      showToast('חניית בדיקה הופעלה');
      await buildChecklist();
    });

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        buildChecklist();
        refreshSamsungCard();
      }
    });
  }

  async function init() {
    bindEvents();
    await Promise.all([buildChecklist(), refreshSamsungCard()]);
  }

  init();
})();
