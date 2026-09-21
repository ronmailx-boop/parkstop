(function () {
  const { showToast, formatTime, formatDistance, escapeHtml } = ParkStopCommon;

  const els = {
    statusPill: document.getElementById('status-pill'),
    statusPillText: document.getElementById('status-pill-text'),
    onboardingBanner: document.getElementById('onboarding-banner'),
    idleView: document.getElementById('idle-view'),
    activeView: document.getElementById('active-view'),
    alertCard: document.getElementById('alert-card'),
    parkingAppSelect: document.getElementById('parking-app-select'),
    customAppField: document.getElementById('custom-app-field'),
    customAppName: document.getElementById('custom-app-name'),
    startBtn: document.getElementById('start-btn'),
    stopBtn: document.getElementById('stop-btn'),
    activeAppName: document.getElementById('active-app-name'),
    activeSince: document.getElementById('active-since'),
    signalSummary: document.getElementById('signal-summary'),
    openAppBtn: document.getElementById('open-app-btn'),
    stoppedBtn: document.getElementById('stopped-btn'),
    snoozeBtn: document.getElementById('snooze-btn'),
  };

  let settings = null;
  let pollTimer = null;

  async function populateAppSelect() {
    els.parkingAppSelect.innerHTML = '';
    settings.parkingApps.forEach((app) => {
      const opt = document.createElement('option');
      opt.value = app.id;
      opt.textContent = app.name;
      els.parkingAppSelect.appendChild(opt);
    });
    const otherOpt = document.createElement('option');
    otherOpt.value = '__other__';
    otherOpt.textContent = 'אחר (הקלדה חופשית)';
    els.parkingAppSelect.appendChild(otherOpt);

    els.parkingAppSelect.value = settings.selectedParkingAppId || settings.parkingApps[0]?.id || '__other__';
    toggleCustomField();
  }

  function toggleCustomField() {
    const isOther = els.parkingAppSelect.value === '__other__';
    els.customAppField.style.display = isOther ? 'block' : 'none';
  }

  function selectedAppInfo() {
    if (els.parkingAppSelect.value === '__other__') {
      return {
        id: '__other__',
        name: els.customAppName.value.trim() || 'אפליקציית חניה',
        packageName: '',
        deepLink: '',
      };
    }
    return settings.parkingApps.find((a) => a.id === els.parkingAppSelect.value) || null;
  }

  function renderStatus(status) {
    const state = status.state || 'IDLE';
    els.statusPill.className = `status-pill ${state.toLowerCase()}`;
    els.statusPillText.textContent = ParkStopCommon.stateLabel(state);

    els.idleView.style.display = 'none';
    els.activeView.style.display = 'none';
    els.alertCard.style.display = 'none';

    if (state === 'IDLE') {
      els.idleView.style.display = 'block';
    } else if (state === 'PARKING_ACTIVE' || state === 'MONITORING') {
      els.activeView.style.display = 'block';
      els.activeAppName.textContent = status.parkingAppName || 'חניה פעילה';
      els.activeSince.textContent = status.startedAt
        ? `הופעלה בשעה ${formatTime(status.startedAt)}`
        : '';
      const parts = [];
      if (status.useBluetooth) {
        parts.push(status.bluetoothConnected ? '🔵 בלוטות׳ הרכב מחובר' : '⚪ בלוטות׳ הרכב מנותק');
      }
      if (status.useGeofence && status.lastDistanceMeters != null) {
        parts.push(`📍 מרחק מהחניה: ${formatDistance(status.lastDistanceMeters)}`);
      }
      els.signalSummary.textContent = parts.join(' · ') || 'ממתין לאיתותים...';
    } else if (state === 'ALERT') {
      els.alertCard.style.display = 'block';
    }
  }

  async function refreshStatus() {
    const status = await ParkStopNative.getStatus();
    renderStatus(status);
    return status;
  }

  async function refreshOnboardingBanner() {
    els.onboardingBanner.style.display = settings.onboardingComplete ? 'none' : 'block';
  }

  async function handleStart() {
    const app = selectedAppInfo();
    if (!app) return;
    if (app.id === '__other__' && !els.customAppName.value.trim()) {
      showToast('נא להזין שם לאפליקציית החניה');
      return;
    }

    settings = await ParkStopStorage.update({ selectedParkingAppId: els.parkingAppSelect.value });

    els.startBtn.disabled = true;
    try {
      await ParkStopNative.startMonitoring({
        parkingAppId: app.id,
        parkingAppName: app.name,
        parkingAppPackage: app.packageName || '',
        parkingAppDeepLink: app.deepLink || '',
        useBluetooth: settings.useBluetooth,
        useGeofence: settings.useGeofence,
        carDeviceAddress: settings.carDevice ? settings.carDevice.address : '',
        carDeviceName: settings.carDevice ? settings.carDevice.name : '',
        geofenceRadiusMeters: settings.geofenceRadiusMeters,
        geofenceBackupWindowMinutes: settings.geofenceBackupWindowMinutes,
      });
      showToast('החניה הופעלה, המעקב פעיל ברקע');
    } finally {
      els.startBtn.disabled = false;
      await refreshStatus();
    }
  }

  async function handleStop() {
    await ParkStopNative.stopMonitoring();
    showToast('החניה נעצרה');
    await refreshStatus();
  }

  async function handleSnooze() {
    await ParkStopNative.snoozeAlert(5);
    showToast('נזכיר לך שוב בעוד 5 דקות');
    await refreshStatus();
  }

  async function handleOpenApp() {
    const status = await ParkStopNative.getStatus();
    await ParkStopNative.openAppDeepLink({
      packageName: status.parkingAppPackage || '',
      deepLink: status.parkingAppDeepLink || '',
      appName: status.parkingAppName || '',
    });
  }

  function bindEvents() {
    els.parkingAppSelect.addEventListener('change', toggleCustomField);
    els.startBtn.addEventListener('click', handleStart);
    els.stopBtn.addEventListener('click', handleStop);
    els.stoppedBtn.addEventListener('click', handleStop);
    els.snoozeBtn.addEventListener('click', handleSnooze);
    els.openAppBtn.addEventListener('click', handleOpenApp);

    ParkStopNative.addListener('statusChanged', (status) => renderStatus(status));

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') refreshStatus();
    });
  }

  async function init() {
    settings = await ParkStopStorage.get();
    await populateAppSelect();
    await refreshOnboardingBanner();
    bindEvents();
    await refreshStatus();
    pollTimer = setInterval(refreshStatus, 5000);
  }

  init();
})();
