(function () {
  const { stateLabel, formatTime, formatDistance, escapeHtml } = ParkStopCommon;

  const els = {
    dsState: document.getElementById('ds-state'),
    dsBluetooth: document.getElementById('ds-bluetooth'),
    dsLocation: document.getElementById('ds-location'),
    dsDistance: document.getElementById('ds-distance'),
    dsUpdated: document.getElementById('ds-updated'),
    logList: document.getElementById('log-list'),
    clearLogsBtn: document.getElementById('clear-logs-btn'),
    shareLogsBtn: document.getElementById('share-logs-btn'),
  };

  const LEVEL_ICON = { info: 'ℹ️', warn: '⚠️', error: '⛔', success: '✅' };

  async function refreshStatus() {
    const status = await ParkStopNative.getStatus();
    els.dsState.textContent = stateLabel(status.state || 'IDLE');
    els.dsBluetooth.textContent = status.bluetoothConnected ? 'מחובר' : 'מנותק';
    els.dsLocation.textContent = status.lastLat != null
      ? `${status.lastLat.toFixed(5)}, ${status.lastLng.toFixed(5)}`
      : '—';
    els.dsDistance.textContent = formatDistance(status.lastDistanceMeters);
    els.dsUpdated.textContent = formatTime(status.lastUpdatedAt);
  }

  function renderLogs(entries) {
    if (!entries || entries.length === 0) {
      els.logList.innerHTML = '<p class="empty-state">אין עדיין אירועים</p>';
      return;
    }
    // Stored oldest-first (each new event is appended); reverse here so the newest
    // entry is the first DOM element, and therefore reliably the first thing shown
    // at the top of the list -- relying on CSS (column-reverse) for this instead
    // was fragile together with the list's own scroll container.
    els.logList.innerHTML = entries
      .slice()
      .reverse()
      .map((e) => {
        const level = e.level || 'info';
        return `<div class="log-entry ${level}"><span class="log-time">${formatTime(e.timestamp)}</span>${LEVEL_ICON[level] || ''} ${escapeHtml(e.message)}</div>`;
      })
      .join('');
  }

  async function refreshLogs() {
    const { entries } = await ParkStopNative.getLogs();
    renderLogs(entries);
  }

  async function refreshAll() {
    await Promise.all([refreshStatus(), refreshLogs()]);
  }

  function bindEvents() {
    els.clearLogsBtn.addEventListener('click', async () => {
      await ParkStopNative.clearLogs();
      await refreshLogs();
    });

    els.shareLogsBtn.addEventListener('click', async () => {
      await ParkStopNative.shareLogs();
    });

    ParkStopNative.addListener('logAdded', refreshLogs);
    ParkStopNative.addListener('statusChanged', refreshStatus);

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') refreshAll();
    });
  }

  async function init() {
    bindEvents();
    await refreshAll();
    setInterval(refreshAll, 4000);
  }

  init();
})();
