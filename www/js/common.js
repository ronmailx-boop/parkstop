(function (global) {
  const STATE_LABELS = {
    IDLE: 'לא פעיל',
    PARKING_ACTIVE: 'חניה פעילה',
    MONITORING: 'עוקב אחרי חזרה לרכב',
    ALERT: 'הגעת לרכב!',
  };

  function stateLabel(state) {
    return STATE_LABELS[state] || state;
  }

  function showToast(message) {
    let el = document.getElementById('ps-toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'ps-toast';
      el.className = 'toast';
      el.setAttribute('role', 'status');
      el.setAttribute('aria-live', 'polite');
      document.body.appendChild(el);
    }
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.remove('show'), 2200);
  }

  function formatTime(ts) {
    if (!ts) return '--:--:--';
    const d = new Date(ts);
    return d.toLocaleTimeString('he-IL', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  function formatDistance(meters) {
    if (meters === null || meters === undefined) return '—';
    if (meters < 1000) return `${Math.round(meters)} מ׳`;
    return `${(meters / 1000).toFixed(1)} ק"מ`;
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str == null ? '' : String(str);
    return div.innerHTML;
  }

  global.ParkStopCommon = { stateLabel, showToast, formatTime, formatDistance, escapeHtml };
})(window);
