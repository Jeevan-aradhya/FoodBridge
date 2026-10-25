(function () {
  'use strict';
  var csrfToken = (document.querySelector('meta[name="_csrf"]') || {}).content;
  var csrfHeader = (document.querySelector('meta[name="_csrf_header"]') || {}).content || 'X-CSRF-TOKEN';

  // ---- expiry countdowns: <span class="countdown" data-deadline="2026-10-22T18:30:00">
  function fmt(ms) {
    if (ms <= 0) return 'Expired';
    var m = Math.floor(ms / 60000), h = Math.floor(m / 60), d = Math.floor(h / 24);
    if (d >= 1) return d + 'd ' + (h % 24) + 'h left';
    if (h >= 1) return h + 'h ' + (m % 60) + 'm left';
    return m + 'm left';
  }
  function tick() {
    document.querySelectorAll('.countdown[data-deadline]').forEach(function (el) {
      var ms = new Date(el.dataset.deadline).getTime() - Date.now();
      el.textContent = fmt(ms);
      el.classList.toggle('expired', ms <= 0);
      el.classList.toggle('urgent', ms > 0 && ms <= 2 * 3600000);
      el.classList.toggle('soon', ms > 2 * 3600000 && ms <= 24 * 3600000);
    });
  }
  tick();
  setInterval(tick, 30000);

  // ---- unread notification badge (Fetch API)
  var badge = document.getElementById('notifBadge');
  function loadUnread() {
    if (!badge) return;
    fetch('/api/notifications/unread-count', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (j) {
        if (!j) return;
        badge.textContent = j.count > 99 ? '99+' : j.count;
        badge.classList.toggle('d-none', j.count === 0);
      }).catch(function () { /* network hiccup: ignore */ });
  }
  loadUnread();
  setInterval(loadUnread, 60000);

  // ---- mark a notification as read without reloading
  document.querySelectorAll('[data-mark-read]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var headers = {};
      headers[csrfHeader] = csrfToken;
      fetch('/api/notifications/' + btn.dataset.markRead + '/read', { method: 'POST', credentials: 'same-origin', headers: headers })
        .then(function (r) {
          if (!r.ok) throw new Error();
          var row = btn.closest('[data-notification]');
          if (row) { row.classList.remove('border-primary'); row.querySelector('.unread-dot')?.remove(); }
          btn.remove();
          loadUnread();
        }).catch(function () { alert('Could not update the notification. Please try again.'); });
    });
  });

  // ---- confirmation dialog for forms with data-confirm
  var modalEl = document.getElementById('confirmModal');
  var pendingForm = null;
  if (modalEl && window.bootstrap) {
    var modal = new bootstrap.Modal(modalEl);
    document.querySelectorAll('form[data-confirm]').forEach(function (f) {
      f.addEventListener('submit', function (e) {
        if (f.dataset.confirmed === '1') return;
        e.preventDefault();
        pendingForm = f;
        document.getElementById('confirmText').textContent = f.dataset.confirm;
        modal.show();
      });
    });
    document.getElementById('confirmOk').addEventListener('click', function () {
      if (pendingForm) { pendingForm.dataset.confirmed = '1'; modal.hide(); pendingForm.requestSubmit(); }
    });
  }

  // ---- loading state on submit buttons
  document.querySelectorAll('form').forEach(function (f) {
    f.addEventListener('submit', function () {
      if (f.hasAttribute('data-confirm') && f.dataset.confirmed !== '1') return;
      var b = f.querySelector('button[type="submit"][data-loading]');
      if (b && f.checkValidity()) {
        b.disabled = true;
        b.classList.add('btn-loading');
        b.insertAdjacentHTML('afterbegin', '<span class="spinner-border me-2" role="status" aria-hidden="true"></span>');
      }
    });
  });

  // ---- image fallback
  document.querySelectorAll('img[data-fallback]').forEach(function (img) {
    img.addEventListener('error', function () { img.onerror = null; img.src = '/images/placeholders/fallback.svg'; });
  });

  // ---- registration form: show fields for the chosen role
  var roleSel = document.querySelectorAll('input[name="role"]');
  function roleFields() {
    var v = (document.querySelector('input[name="role"]:checked') || {}).value;
    document.querySelectorAll('[data-role-only]').forEach(function (el) {
      var roles = el.dataset.roleOnly.split(',');
      el.classList.toggle('d-none', roles.indexOf(v) < 0);
    });
  }
  roleSel.forEach(function (r) { r.addEventListener('change', roleFields); });
  if (roleSel.length) roleFields();

  // ---- weekly chart (Chart.js) fed by /api/reports/weekly
  var canvas = document.getElementById('weeklyChart');
  if (canvas && window.Chart) {
    fetch('/api/reports/weekly', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        new Chart(canvas, {
          type: 'bar',
          data: {
            labels: d.labels,
            datasets: [
              { label: 'Donations listed', data: d.donationsListed, backgroundColor: '#2f7d52', borderRadius: 6 },
              { label: 'Units delivered', data: d.unitsDelivered, backgroundColor: '#d9701c', borderRadius: 6 }
            ]
          },
          options: { responsive: true, maintainAspectRatio: false,
            scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
            plugins: { legend: { position: 'bottom' } } }
        });
      }).catch(function () {
        canvas.parentElement.innerHTML = '<p class="text-muted mb-0">The chart could not be loaded.</p>';
      });
  }
})();
