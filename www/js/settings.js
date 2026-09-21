(function () {
  const { showToast, escapeHtml } = ParkStopCommon;

  const els = {
    btDeviceSelect: document.getElementById('bt-device-select'),
    refreshDevicesBtn: document.getElementById('refresh-devices-btn'),
    useBluetoothToggle: document.getElementById('use-bluetooth-toggle'),
    useGeofenceToggle: document.getElementById('use-geofence-toggle'),
    geofenceRadius: document.getElementById('geofence-radius'),
    geofenceWindow: document.getElementById('geofence-window'),
    parkingAppsList: document.getElementById('parking-apps-list'),
    newAppName: document.getElementById('new-app-name'),
    newAppPackage: document.getElementById('new-app-package'),
    addAppBtn: document.getElementById('add-app-btn'),
  };

  let settings = null;

  async function loadDevices(preserveSelection) {
    const { devices } = await ParkStopNative.getPairedDevices();
    els.btDeviceSelect.innerHTML = '';

    if (!devices || devices.length === 0) {
      const opt = document.createElement('option');
      opt.value = '';
      opt.textContent = 'לא נמצאו מכשירים מזווגים';
      els.btDeviceSelect.appendChild(opt);
      return;
    }

    const noneOpt = document.createElement('option');
    noneOpt.value = '';
    noneOpt.textContent = '— לא נבחר —';
    els.btDeviceSelect.appendChild(noneOpt);

    devices.forEach((d) => {
      const opt = document.createElement('option');
      opt.value = d.address;
      opt.textContent = `${d.name} (${d.address})`;
      els.btDeviceSelect.appendChild(opt);
    });

    const currentAddress = preserveSelection && settings.carDevice ? settings.carDevice.address : '';
    if (currentAddress) els.btDeviceSelect.value = currentAddress;
  }

  function renderParkingApps() {
    els.parkingAppsList.innerHTML = '';
    if (settings.parkingApps.length === 0) {
      els.parkingAppsList.innerHTML = '<p class="empty-state">אין עדיין אפליקציות שמורות</p>';
      return;
    }
    settings.parkingApps.forEach((app) => {
      const row = document.createElement('div');
      row.className = 'list-item';
      row.innerHTML = `
        <div>
          <div class="row-title">${escapeHtml(app.name)}</div>
          <div class="row-sub">${escapeHtml(app.packageName || 'ללא package name')}</div>
        </div>
        <button class="btn btn-secondary" style="width:auto;padding:8px 14px;" data-remove="${app.id}">הסר</button>
      `;
      els.parkingAppsList.appendChild(row);
    });

    els.parkingAppsList.querySelectorAll('[data-remove]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const id = btn.getAttribute('data-remove');
        settings.parkingApps = settings.parkingApps.filter((a) => a.id !== id);
        await ParkStopStorage.set(settings);
        renderParkingApps();
        showToast('האפליקציה הוסרה');
      });
    });
  }

  async function saveField(partial) {
    settings = await ParkStopStorage.update(partial);
  }

  function bindEvents() {
    els.refreshDevicesBtn.addEventListener('click', () => loadDevices(true));

    els.btDeviceSelect.addEventListener('change', async () => {
      const address = els.btDeviceSelect.value;
      const label = els.btDeviceSelect.options[els.btDeviceSelect.selectedIndex]?.textContent || '';
      const name = label.replace(/\s*\([^)]*\)\s*$/, '');
      await saveField({ carDevice: address ? { address, name } : null });
      showToast('נשמר');
    });

    els.useBluetoothToggle.addEventListener('change', async () => {
      await saveField({ useBluetooth: els.useBluetoothToggle.checked });
    });

    els.useGeofenceToggle.addEventListener('change', async () => {
      await saveField({ useGeofence: els.useGeofenceToggle.checked });
    });

    els.geofenceRadius.addEventListener('change', async () => {
      const val = Math.max(10, Math.min(500, Number(els.geofenceRadius.value) || 50));
      els.geofenceRadius.value = val;
      await saveField({ geofenceRadiusMeters: val });
    });

    els.geofenceWindow.addEventListener('change', async () => {
      const val = Math.max(1, Math.min(60, Number(els.geofenceWindow.value) || 10));
      els.geofenceWindow.value = val;
      await saveField({ geofenceBackupWindowMinutes: val });
    });

    els.addAppBtn.addEventListener('click', async () => {
      const name = els.newAppName.value.trim();
      if (!name) {
        showToast('נא להזין שם אפליקציה');
        return;
      }
      const packageName = els.newAppPackage.value.trim();
      const id = `custom_${Date.now()}`;
      settings.parkingApps.push({ id, name, packageName, deepLink: '' });
      await ParkStopStorage.set(settings);
      els.newAppName.value = '';
      els.newAppPackage.value = '';
      renderParkingApps();
      showToast('האפליקציה נוספה');
    });
  }

  async function init() {
    settings = await ParkStopStorage.get();
    els.useBluetoothToggle.checked = settings.useBluetooth;
    els.useGeofenceToggle.checked = settings.useGeofence;
    els.geofenceRadius.value = settings.geofenceRadiusMeters;
    els.geofenceWindow.value = settings.geofenceBackupWindowMinutes;
    renderParkingApps();
    await loadDevices(true);
    bindEvents();
  }

  init();
})();
