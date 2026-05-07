/* ============================================================
   dashboard.js – Dashboard logic & API integration
   API base comes from window.INTERVIEW_API_BASE when deployed separately.
   ============================================================ */

const API = (window.INTERVIEW_API_BASE || '').replace(/\/$/, '');

let currentUser  = null;
let displayedUsers = [];  // cache for client-side filter

/* ============================================================
   INIT – guard auth, bootstrap UI
   ============================================================ */
window.addEventListener('DOMContentLoaded', () => {
  currentUser = getStoredUser();

  if (!currentUser || !currentUser.id) {
    // Not logged in – redirect to landing
    window.location.href = '../index.html';
    return;
  }

  initUI();
  showSection('overview');
  loadOverview();
});

function getStoredUser() {
  try { return JSON.parse(localStorage.getItem('ip_user')); }
  catch { return null; }
}

function logout() {
  localStorage.removeItem('ip_user');
  window.location.href = '../index.html';
}

/* ── Initialise static UI bits ── */
function initUI() {
  document.getElementById('welcome-heading').textContent =
    `Welcome back, ${currentUser.name || currentUser.email}!`;
  const role = (currentUser.role || '').toLowerCase();

  document.getElementById('welcome-sub').textContent =
    role === 'interviewer'
      ? 'Manage your upcoming interview sessions.'
      : 'Find interviewers and schedule practice sessions.';

  document.getElementById('sidebar-user-info').innerHTML =
    `<strong>${currentUser.name || '—'}</strong><br/>${currentUser.email}<br/>
     <span class="badge badge-purple" style="margin-top:0.3rem;">${role || 'user'}</span>`;

  // Pre-fill interviewee ID in schedule form
  if (document.getElementById('ses-interviewer')) {
    // noop – interviewer ID is entered manually or via button
  }

  // Set datetime min to now
  const dtInput = document.getElementById('ses-date');
  if (dtInput) {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    dtInput.min = now.toISOString().slice(0, 16);
  }
}

/* ============================================================
   SECTION NAVIGATION
   ============================================================ */
function showSection(name) {
  // Hide all sections
  document.querySelectorAll('.dashboard-section').forEach(s => s.style.display = 'none');
  // Deactivate all nav links
  document.querySelectorAll('.nav-link').forEach(b => {
    b.classList.remove('active');
    b.removeAttribute('aria-current');
  });

  // Show target
  const sec = document.getElementById(`section-${name}`);
  if (sec) sec.style.display = 'block';

  const navBtn = document.getElementById(`nav-${name}`);
  if (navBtn) { navBtn.classList.add('active'); navBtn.setAttribute('aria-current', 'page'); }

  // Lazy-load section data
  if (name === 'interviewers') loadInterviewers();
  if (name === 'sessions')     loadMySessions();
  if (name === 'feedback')     loadFeedback();
}

/* ============================================================
   ALERT helpers
   ============================================================ */
function showAlert(id, message, type = 'error') {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = message;
  el.className = `alert alert-${type} show`;
  if (type !== 'error') setTimeout(() => el.classList.remove('show'), 4000);
}

function hideAlert(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove('show');
}

function setLoading(btn, loading, label = 'Please wait...') {
  if (!btn) return;
  if (loading) {
    btn.disabled = true;
    btn.dataset.originalText = btn.textContent;
    btn.innerHTML = `<span class="spinner"></span> ${label}`;
  } else {
    btn.disabled = false;
    btn.textContent = btn.dataset.originalText;
  }
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 45000) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}

/* ============================================================
   STATUS BADGE helper
   ============================================================ */
function statusBadge(status) {
  const map = {
    pending:   'badge-yellow',
    confirmed: 'badge-purple',
    completed: 'badge-green',
    cancelled: 'badge-red',
  };
  const cls = map[(status || '').toLowerCase()] || 'badge-gray';
  return `<span class="badge ${cls}">${status || 'unknown'}</span>`;
}

/* ============================================================
   FORMAT DATE
   ============================================================ */
function fmtDate(str) {
  if (!str) return '—';
  try {
    return new Date(str).toLocaleString(undefined, {
      dateStyle: 'medium', timeStyle: 'short'
    });
  } catch { return str; }
}

/* ============================================================
   OVERVIEW
   Load stats + recent sessions for the logged-in user
   ============================================================ */
async function loadOverview() {
  try {
    const sessions = await fetchMySessions();
    const total     = sessions.length;
    const confirmed = sessions.filter(s => (s.status || '').toLowerCase() === 'confirmed').length;
    const completed = sessions.filter(s => (s.status || '').toLowerCase() === 'completed').length;

    document.getElementById('stat-sessions').textContent  = total;
    document.getElementById('stat-confirmed').textContent = confirmed;
    document.getElementById('stat-completed').textContent = completed;

    const recent = sessions.slice(0, 5);
    const container = document.getElementById('overview-sessions-list');

    if (recent.length === 0) {
      container.innerHTML = `<div class="empty-state"><div class="icon">📭</div><p>No sessions yet. <a href="#" onclick="showSection('schedule'); return false;">Schedule one!</a></p></div>`;
      return;
    }

    container.innerHTML = buildSessionTable(recent, { compact: true });
  } catch (err) {
    console.error('Overview load error:', err);
  }
}

/* ============================================================
   USERS / INTERVIEWERS
   GET /api/users/interviewers?skill=
   ============================================================ */
async function loadUsers() {
  const grid  = document.getElementById('interviewers-grid');
  const alert = 'interviewers-alert';
  hideAlert(alert);
  grid.innerHTML = `<div class="empty-state"><div class="icon">⏳</div><p>Loading...</p></div>`;

  try {
    const res = await fetchWithTimeout(`${API}/api/users`);
    if (!res.ok) throw new Error(`Server responded with ${res.status}`);

    displayedUsers = unwrapApiResponse(await res.json());
    renderUsers(displayedUsers);
  } catch (err) {
    grid.innerHTML = '';
    showAlert(alert, 'Could not load users. Is the backend running?');
    console.error(err);
  }
}

async function loadInterviewers(skill = '') {
  const grid  = document.getElementById('interviewers-grid');
  const alert = 'interviewers-alert';
  hideAlert(alert);
  grid.innerHTML = `<div class="empty-state"><div class="icon">⏳</div><p>Loading...</p></div>`;

  try {
    const url = skill
      ? `${API}/api/users/interviewers?skill=${encodeURIComponent(skill)}`
      : `${API}/api/users/interviewers`;

    const res = await fetchWithTimeout(url);

    if (!res.ok) throw new Error(`Server responded with ${res.status}`);

    displayedUsers = unwrapApiResponse(await res.json());
    renderUsers(displayedUsers);
  } catch (err) {
    grid.innerHTML = '';
    showAlert(alert, 'Could not load interviewers. Is the backend running?');
    console.error(err);
  }
}

function filterInterviewers(query) {
  if (!query.trim()) {
    renderUsers(displayedUsers);
    return;
  }
  const q = query.toLowerCase();
  const filtered = displayedUsers.filter(u =>
    (u.name  || '').toLowerCase().includes(q) ||
    (u.username || '').toLowerCase().includes(q) ||
    (u.role || '').toLowerCase().includes(q) ||
    (u.email || '').toLowerCase().includes(q) ||
    (u.skills || []).some(s => s.toLowerCase().includes(q))
  );
  renderUsers(filtered);
}

function renderUsers(list) {
  const grid = document.getElementById('interviewers-grid');

  if (!list || list.length === 0) {
    grid.innerHTML = `<div class="empty-state"><div class="icon">🔍</div><p>No users found.</p></div>`;
    return;
  }

  grid.innerHTML = list.map(u => `
    <div class="user-card">
      <div class="user-card-info">
        <div class="name">${escHtml(u.name || u.username || '-')}</div>
        <div class="email">${escHtml(u.email || '—')}</div>
        <div class="email">${escHtml((u.role || 'USER').toLowerCase())}</div>
        ${u.skills && u.skills.length
          ? `<div class="skills">${u.skills.map(s => escHtml(s)).join(' · ')}</div>`
          : ''}
      </div>
      <div style="display:flex; flex-direction:column; gap:0.4rem; align-items:flex-end;">
        <small style="color:var(--text-muted); font-size:0.72rem; word-break:break-all;">ID: ${escHtml(u.id || u._id || '?')}</small>
        ${(u.role || '').toLowerCase() === 'interviewer'
          ? `<button class="btn btn-primary btn-sm" onclick="prefillSchedule('${escHtml(u.id || u._id || '')}')">Book Session</button>`
          : ''}
      </div>
    </div>
  `).join('');
}

/* ── Pre-fill schedule form with interviewer ID ── */
function prefillSchedule(interviewerId) {
  document.getElementById('ses-interviewer').value = interviewerId;
  showSection('schedule');
}

/* ============================================================
   CREATE SESSION
   POST /api/sessions
   Body: { interviewerId, intervieweeId, topic, scheduledAt, notes }
   ============================================================ */
document.getElementById('session-form').addEventListener('submit', async function (e) {
  e.preventDefault();

  const interviewerId = document.getElementById('ses-interviewer').value.trim();
  const topic         = document.getElementById('ses-topic').value.trim();
  const scheduledAt   = document.getElementById('ses-date').value;
  const notes         = document.getElementById('ses-notes').value.trim();
  const btn           = document.getElementById('session-submit');

  hideAlert('schedule-alert');

  if (!interviewerId || !topic || !scheduledAt) {
    showAlert('schedule-alert', 'Please fill in all required fields.');
    return;
  }

  const payload = {
    interviewerId,
    intervieweeId: currentUser.id,
    title: topic,
    topic,
    startTime: scheduledAt,
    scheduledAt,
    notes,
  };

  setLoading(btn, true, 'Requesting…');

  try {
    const res = await fetchWithTimeout(`${API}/api/sessions`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify(payload),
    });

    if (res.ok) {
      showAlert('schedule-alert', 'Session request sent! Check "My Sessions" for updates.', 'success');
      document.getElementById('session-form').reset();
    } else {
      showAlert('schedule-alert', await readApiError(res, 'Failed to create session. Please try again.'));
    }
  } catch (err) {
    showAlert('schedule-alert', 'Could not connect to the server. Is the backend running?');
    console.error(err);
  } finally {
    setLoading(btn, false);
  }
});

/* ============================================================
   MY SESSIONS
   GET /api/sessions/interviewee/{id}  or  /api/sessions/interviewer/{id}
   ============================================================ */
async function fetchMySessions() {
  const role     = (currentUser.role || '').toLowerCase();
  const endpoint = role === 'interviewer'
    ? `${API}/api/sessions/interviewer/${currentUser.id}`
    : `${API}/api/sessions/interviewee/${currentUser.id}`;

  const res = await fetchWithTimeout(endpoint);
  if (!res.ok) throw new Error(`Server error ${res.status}`);
  return unwrapApiResponse(await res.json());
}

async function loadMySessions() {
  const wrapper = document.getElementById('sessions-table-wrapper');
  hideAlert('sessions-alert');
  wrapper.innerHTML = `<div class="empty-state"><div class="icon">⏳</div><p>Loading…</p></div>`;

  try {
    const sessions = await fetchMySessions();
    if (sessions.length === 0) {
      wrapper.innerHTML = `<div class="empty-state"><div class="icon">📭</div><p>No sessions yet.</p></div>`;
      return;
    }
    wrapper.innerHTML = buildSessionTable(sessions, { compact: false });
  } catch (err) {
    wrapper.innerHTML = '';
    showAlert('sessions-alert', 'Could not load sessions. Is the backend running?');
    console.error(err);
  }
}

/* ── Build sessions HTML table ── */
function buildSessionTable(sessions, { compact = false } = {}) {
  const isInterviewer = (currentUser.role || '').toLowerCase() === 'interviewer';

  const rows = sessions.map(s => {
    const id     = s.id || s._id || '—';
    const other  = isInterviewer
      ? (s.intervieweeName || s.intervieweeId || s.candidateId || '-')
      : (s.interviewerName || s.interviewerId || '-');
    const label  = isInterviewer ? 'Interviewee' : 'Interviewer';

    let actions = '';
    if (!compact) {
      const st = (s.status || '').toLowerCase();
      if (st === 'pending' && isInterviewer) {
        actions = `
          <button class="btn btn-success btn-sm" onclick="sessionAction('${id}','confirm')">Confirm</button>
          <button class="btn btn-danger btn-sm" style="margin-left:0.4rem" onclick="sessionAction('${id}','cancel')">Cancel</button>
        `;
      } else if (st === 'confirmed') {
        actions = `<button class="btn btn-primary btn-sm" onclick="sessionAction('${id}','complete')">Complete</button>`;
        if (isInterviewer) actions += `<button class="btn btn-danger btn-sm" style="margin-left:0.4rem" onclick="sessionAction('${id}','cancel')">Cancel</button>`;
      }
    }

    return `
      <tr>
        <td><small style="color:var(--text-muted); font-size:0.72rem;">${escHtml(String(id))}</small></td>
        <td>${escHtml(String(other))}</td>
        <td>${escHtml(s.topic || s.title || '-')}</td>
        <td>${fmtDate(s.scheduledAt || s.startTime)}</td>
        <td>${statusBadge(s.status)}</td>
        ${!compact ? `<td>${actions || '-'}</td>` : ''}
      </tr>
    `;
  }).join('');

  const isInterviewerLabel = isInterviewer ? 'Interviewee' : 'Interviewer';

  return `
    <div style="overflow-x:auto;">
      <table>
        <thead>
          <tr>
            <th>Session ID</th>
            <th>${isInterviewerLabel}</th>
            <th>Topic</th>
            <th>Scheduled</th>
            <th>Status</th>
            ${!compact ? '<th>Actions</th>' : ''}
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>
  `;
}

/* ============================================================
   SESSION ACTIONS
   PATCH /api/sessions/{id}/confirm|complete|cancel
   ============================================================ */
let pendingAction = null;

function sessionAction(sessionId, action) {
  pendingAction = { sessionId, action };

  const labels = { confirm: 'Confirm', complete: 'Complete', cancel: 'Cancel' };
  document.getElementById('action-modal-title').textContent = `${labels[action]} Session`;
  document.getElementById('action-modal-body').textContent  =
    `Are you sure you want to ${action} this session?`;

  const confirmBtn = document.getElementById('action-confirm-btn');
  confirmBtn.className = action === 'cancel'
    ? 'btn btn-danger btn-sm'
    : action === 'complete' ? 'btn btn-success btn-sm' : 'btn btn-primary btn-sm';
  confirmBtn.textContent = labels[action];

  const overlay = document.getElementById('action-overlay');
  overlay.style.display = 'flex';
}

function closeModal() {
  document.getElementById('action-overlay').style.display = 'none';
  pendingAction = null;
}

document.getElementById('action-confirm-btn').addEventListener('click', async () => {
  if (!pendingAction) return;
  const { sessionId, action } = pendingAction;
  closeModal();

  try {
    const res = await fetchWithTimeout(`${API}/api/sessions/${sessionId}/${action}`, {
      method: 'PATCH',
    });

    if (res.ok) {
      showAlert('sessions-alert', `Session ${action}ed successfully.`, 'success');
      loadMySessions();
      loadOverview();
    } else {
      showAlert('sessions-alert', await readApiError(res, `Failed to ${action} session.`));
    }
  } catch (err) {
    showAlert('sessions-alert', 'Could not connect to the server.');
    console.error(err);
  }
});

// Close modal on overlay click
document.getElementById('action-overlay').addEventListener('click', function (e) {
  if (e.target === this) closeModal();
});

/* ============================================================
   FEEDBACK
   POST /api/feedback, GET /api/feedback
   ============================================================ */
document.getElementById('feedback-form')?.addEventListener('submit', async function (e) {
  e.preventDefault();

  const sessionId = document.getElementById('fb-session').value.trim();
  const rating = Number(document.getElementById('fb-rating').value);
  const comments = document.getElementById('fb-comments').value.trim();
  const strengths = document.getElementById('fb-strengths').value.trim();
  const weaknesses = document.getElementById('fb-weaknesses').value.trim();
  const btn = document.getElementById('feedback-submit');

  hideAlert('feedback-alert');

  if (!sessionId || !rating || !comments) {
    showAlert('feedback-alert', 'Session ID, rating, and comments are required.');
    return;
  }

  setLoading(btn, true, 'Submitting...');

  try {
    const res = await fetchWithTimeout(`${API}/api/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId,
        reviewerId: currentUser.id,
        rating,
        comments,
        strengths,
        weaknesses,
      }),
    });

    if (!res.ok) {
      throw new Error(await readApiError(res, 'Feedback submission failed.'));
    }

    showAlert('feedback-alert', 'Feedback submitted successfully.', 'success');
    document.getElementById('feedback-form').reset();
    loadFeedback();
  } catch (err) {
    showAlert('feedback-alert', err.message || 'Could not submit feedback.');
    console.error(err);
  } finally {
    setLoading(btn, false);
  }
});

async function loadFeedback() {
  const wrapper = document.getElementById('feedback-list');
  if (!wrapper) return;

  wrapper.innerHTML = `<div class="empty-state"><div class="icon">⏳</div><p>Loading feedback...</p></div>`;

  try {
    const res = await fetchWithTimeout(`${API}/api/feedback`);
    if (!res.ok) throw new Error(`Server error ${res.status}`);
    const feedback = unwrapApiResponse(await res.json());

    if (!feedback.length) {
      wrapper.innerHTML = `<div class="empty-state"><div class="icon">📭</div><p>No feedback yet.</p></div>`;
      return;
    }

    wrapper.innerHTML = feedback.map(item => `
      <div class="user-card">
        <div class="user-card-info">
          <div class="name">Session ${escHtml(item.sessionId || '-')} · ${escHtml(String(item.rating || '-'))}/5</div>
          <div class="email">Reviewer: ${escHtml(item.reviewerId || '-')}</div>
          <div class="skills">${escHtml(item.comments || '')}</div>
          ${item.strengths ? `<small>Strengths: ${escHtml(item.strengths)}</small>` : ''}
          ${item.weaknesses ? `<small>Needs work: ${escHtml(item.weaknesses)}</small>` : ''}
        </div>
      </div>
    `).join('');
  } catch (err) {
    wrapper.innerHTML = '';
    showAlert('feedback-alert', 'Could not load feedback.');
    console.error(err);
  }
}

/* ============================================================
   UTILITIES
   ============================================================ */
function escHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function unwrapApiResponse(payload) {
  return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload;
}

async function readApiError(res, fallback) {
  try {
    const payload = await res.json();
    return payload.message || fallback;
  } catch {
    const text = await res.text();
    return text || fallback;
  }
}
