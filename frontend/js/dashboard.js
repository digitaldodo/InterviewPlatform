const API_BASE = window.INTERVIEW_API_BASE;

let currentUser = null;
let sessions = [];
let feedbackItems = [];
let interviewers = [];
let interviewerPage = 0;
let interviewerTotalPages = 1;
let selectedInterviewer = null;
let bookingStep = 1;
let bookingState = { interviewer: null, interviewType: '', startTime: '' };
let searchTimer = null;

window.addEventListener('DOMContentLoaded', async () => {
  currentUser = readJson('ip_user');
  if (!currentUser?.id) {
    window.location.href = '../index.html';
    return;
  }
  initUi();
  bindFilters();
  bindFeedbackForm();
  await Promise.all([loadSessions(), loadInterviewers(), loadRecommended(), loadFeedback(), loadNotifications()]);
  showSection('overview');
});

function readJson(key) {
  try { return JSON.parse(localStorage.getItem(key)); } catch { return null; }
}

function logout() {
  localStorage.removeItem('ip_user');
  localStorage.removeItem('ip_access_token');
  localStorage.removeItem('ip_refresh_token');
  window.location.href = '../index.html';
}

function initUi() {
  const role = (currentUser.role || 'INTERVIEWEE').toLowerCase();
  document.getElementById('welcome-heading').textContent = `Welcome back, ${currentUser.name || currentUser.username || currentUser.email}`;
  document.getElementById('role-eyebrow').textContent = role === 'interviewer' ? 'Interviewer workspace' : 'Interviewee workspace';
  document.getElementById('sidebar-user-info').innerHTML = `<strong>${esc(currentUser.name || currentUser.username || 'User')}</strong><span>${esc(currentUser.email || '')}</span><span class="badge badge-purple">${esc(role)}</span>`;
  document.querySelectorAll('.interviewer-only').forEach(el => el.style.display = role === 'interviewer' ? 'flex' : 'none');
  renderBookingStep();
}

function toggleSidebar(forceOpen) {
  const sidebar = document.getElementById('sidebar');
  if (typeof forceOpen === 'boolean') {
    sidebar.classList.toggle('open', forceOpen);
    return;
  }
  sidebar.classList.toggle('open');
}

function showSection(name) {
  document.querySelectorAll('.dashboard-section').forEach(section => section.hidden = true);
  document.getElementById(`section-${name}`).hidden = false;
  document.querySelectorAll('.nav-link').forEach(link => link.classList.toggle('active', link.dataset.section === name));
  if (name === 'booking') renderBookingStep();
  if (name === 'feedback') populateFeedbackSessions();
  if (name === 'sessions') renderSessions('upcoming');
  if (window.innerWidth < 900) document.getElementById('sidebar').classList.remove('open');
}

async function api(path, options = {}, retry = true) {
  const token = localStorage.getItem('ip_access_token');
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  });
  const payload = await readPayload(res);
  if (res.status === 401 && retry && localStorage.getItem('ip_refresh_token')) {
    await refreshToken();
    return api(path, options, false);
  }
  if (!res.ok) throw new Error(payload?.message || `Request failed (${res.status})`);
  return payload?.data ?? payload;
}

async function refreshToken() {
  const data = await api('/api/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: localStorage.getItem('ip_refresh_token') }),
  }, false);
  localStorage.setItem('ip_access_token', data.accessToken);
  localStorage.setItem('ip_refresh_token', data.refreshToken);
}

async function readPayload(res) {
  const text = await res.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
}

function bindFilters() {
  ['search-q', 'filter-expertise', 'filter-company', 'filter-language', 'filter-rating', 'filter-available', 'filter-free', 'filter-sort']
    .forEach(id => {
      const el = document.getElementById(id);
      el.addEventListener(el.type === 'checkbox' ? 'change' : 'input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
          interviewerPage = 0;
          loadInterviewers();
        }, 280);
      });
    });
}

async function loadInterviewers() {
  const grid = document.getElementById('interviewer-grid');
  grid.innerHTML = skeletonCards(6);
  try {
    const params = new URLSearchParams({
      q: val('search-q'),
      expertise: val('filter-expertise'),
      company: val('filter-company'),
      language: val('filter-language'),
      sort: val('filter-sort'),
      page: interviewerPage,
      size: 9,
    });
    if (val('filter-rating')) params.set('minRating', val('filter-rating'));
    if (document.getElementById('filter-available').checked) params.set('available', 'true');
    if (document.getElementById('filter-free').checked) params.set('free', 'true');
    const page = await api(`/api/interviewers/search?${params.toString()}`);
    interviewers = page.items || [];
    interviewerTotalPages = Math.max(1, page.totalPages || 1);
    renderInterviewerGrid(interviewers);
    document.getElementById('page-label').textContent = `Page ${interviewerPage + 1} of ${interviewerTotalPages}`;
  } catch (err) {
    grid.innerHTML = emptyState('Could not load interviewers.');
    toast(err.message, 'error');
  }
}

async function loadRecommended() {
  const list = document.getElementById('recommended-list');
  list.innerHTML = skeletonCards(3);
  try {
    const data = await api(`/api/interviewers/recommended?intervieweeId=${currentUser.id}`);
    list.innerHTML = (data || []).slice(0, 4).map(renderCompactInterviewer).join('') || emptyState('No recommendations yet.');
  } catch {
    list.innerHTML = emptyState('Recommendations will appear here.');
  }
}

function renderInterviewerGrid(list) {
  const grid = document.getElementById('interviewer-grid');
  if (!list.length) {
    grid.innerHTML = emptyState('No interviewers match those filters.');
    return;
  }
  grid.classList.remove('skeleton-grid');
  grid.innerHTML = list.map(interviewer => `
    <article class="interviewer-card">
      <div class="card-top">
        <div class="avatar">${initials(interviewer)}</div>
        <button class="icon-btn" title="Save favorite" onclick="favoriteInterviewer('${interviewer.id}')">♡</button>
      </div>
      <h3>${esc(interviewer.name || interviewer.username || 'Interviewer')}</h3>
      <p>${esc(interviewer.currentRole || 'Interview coach')} ${interviewer.company ? `at ${esc(interviewer.company)}` : ''}</p>
      <div class="rating-row"><strong>${Number(interviewer.averageRating || 0).toFixed(1)}</strong><span>${interviewer.reviewCount || 0} reviews</span><span>${interviewer.completedInterviews || 0} sessions</span></div>
      <div class="tag-row">${(interviewer.skills || []).slice(0, 4).map(skill => `<span>${esc(skill)}</span>`).join('')}</div>
      <p class="bio">${esc(interviewer.bio || 'Experienced interviewer available for focused mock interview preparation.')}</p>
      <div class="card-actions">
        <button class="btn btn-outline btn-sm" onclick="openProfile('${interviewer.id}')">Profile</button>
        <button class="btn btn-primary btn-sm" onclick="selectInterviewer('${interviewer.id}')">Book</button>
      </div>
    </article>
  `).join('');
}

function renderCompactInterviewer(interviewer) {
  return `
    <button class="mini-interviewer" onclick="selectInterviewer('${interviewer.id}')">
      <span class="avatar">${initials(interviewer)}</span>
      <span><strong>${esc(interviewer.name || interviewer.username || 'Interviewer')}</strong><small>${esc(interviewer.currentRole || 'Interview coach')}</small></span>
    </button>
  `;
}

function changePage(delta) {
  interviewerPage = Math.min(Math.max(0, interviewerPage + delta), interviewerTotalPages - 1);
  loadInterviewers();
}

async function openProfile(id) {
  try {
    const interviewer = await api(`/api/interviewers/${id}`);
    modal(`
      <div class="profile-modal">
        <div class="avatar large">${initials(interviewer)}</div>
        <h2>${esc(interviewer.name || interviewer.username || 'Interviewer')}</h2>
        <p>${esc(interviewer.currentRole || 'Interview coach')} ${interviewer.company ? `at ${esc(interviewer.company)}` : ''}</p>
        <div class="tag-row">${(interviewer.skills || []).map(skill => `<span>${esc(skill)}</span>`).join('')}</div>
        <p>${esc(interviewer.bio || 'No bio yet.')}</p>
        <div class="stats-inline"><span>${interviewer.yearsExperience || 0}+ years</span><span>${Number(interviewer.averageRating || 0).toFixed(1)} rating</span><span>${interviewer.completedInterviews || 0} completed</span></div>
        <button class="btn btn-primary btn-full" onclick="closeModal(); selectInterviewer('${interviewer.id}')">Book this interviewer</button>
      </div>
    `);
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function favoriteInterviewer(id) {
  try {
    const user = await api(`/api/interviewers/${id}/favorite`, {
      method: 'POST',
      body: JSON.stringify({ userId: currentUser.id }),
    });
    currentUser = user;
    localStorage.setItem('ip_user', JSON.stringify(user));
    toast('Saved interviewers updated.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  }
}

function selectInterviewer(id) {
  selectedInterviewer = interviewers.find(item => item.id === id) || null;
  bookingState.interviewer = selectedInterviewer || { id };
  bookingStep = 2;
  showSection('booking');
}

function renderBookingStep() {
  document.querySelectorAll('.step').forEach(step => step.classList.toggle('active', Number(step.dataset.step) === bookingStep));
  const host = document.getElementById('booking-step-content');
  if (bookingStep === 1) {
    host.innerHTML = `<h2>Choose interviewer</h2><div class="interviewer-grid compact">${interviewers.slice(0, 6).map(item => `
      <article class="mini-card"><div class="avatar">${initials(item)}</div><strong>${esc(item.name || item.username || 'Interviewer')}</strong><small>${esc(item.currentRole || '')}</small><button class="btn btn-primary btn-sm" onclick="selectInterviewer('${item.id}')">Choose</button></article>
    `).join('') || emptyState('Search for interviewers first.')}</div>`;
  }
  if (bookingStep === 2) {
    const types = ['DSA', 'System Design', 'HR', 'Frontend', 'Backend', 'Behavioral', 'Resume Review'];
    host.innerHTML = `<h2>Choose interview type</h2><div class="type-grid">${types.map(type => `<button class="type-card" onclick="chooseType('${type}')">${type}</button>`).join('')}</div>`;
  }
  if (bookingStep === 3) renderSlotStep();
  if (bookingStep === 4) {
    host.innerHTML = `
      <h2>Confirm booking</h2>
      <div class="confirm-box">
        <p><strong>Interviewer</strong><span>${esc(bookingState.interviewer?.name || bookingState.interviewer?.username || 'Selected interviewer')}</span></p>
        <p><strong>Type</strong><span>${esc(bookingState.interviewType)}</span></p>
        <p><strong>Slot</strong><span>${fmtDate(bookingState.startTime)}</span></p>
      </div>
      <textarea id="booking-notes" placeholder="Optional goals or context"></textarea>
      <button class="btn btn-primary btn-full sticky-action" onclick="confirmBooking()">Confirm booking</button>
    `;
  }
}

function chooseType(type) {
  bookingState.interviewType = type;
  bookingStep = 3;
  renderBookingStep();
}

async function renderSlotStep() {
  const host = document.getElementById('booking-step-content');
  host.innerHTML = `<h2>Choose a slot</h2><div class="slot-grid">${skeletonCards(4)}</div>`;
  try {
    const slots = await api(`/api/interviewers/${bookingState.interviewer.id}/availability`);
    const fallback = defaultSlots();
    const options = slots.length ? slots : fallback;
    host.innerHTML = `<h2>Choose a slot</h2><div class="slot-grid">${options.map(slot => `<button onclick="chooseSlot('${slot}')">${fmtDate(slot)}</button>`).join('')}</div>`;
  } catch {
    const options = defaultSlots();
    host.innerHTML = `<h2>Choose a slot</h2><div class="slot-grid">${options.map(slot => `<button onclick="chooseSlot('${slot}')">${fmtDate(slot)}</button>`).join('')}</div>`;
  }
}

function chooseSlot(slot) {
  bookingState.startTime = slot;
  bookingStep = 4;
  renderBookingStep();
}

async function confirmBooking() {
  try {
    const session = await api('/api/bookings', {
      method: 'POST',
      body: JSON.stringify({
        interviewerId: bookingState.interviewer.id,
        intervieweeId: currentUser.id,
        interviewType: bookingState.interviewType,
        startTime: bookingState.startTime,
        notes: document.getElementById('booking-notes').value.trim(),
      }),
    });
    toast('Booking requested. Meeting link is ready.', 'success');
    sessions.unshift(session);
    bookingStep = 1;
    bookingState = { interviewer: null, interviewType: '', startTime: '' };
    await loadSessions();
    showSection('sessions');
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function loadSessions() {
  try {
    const role = (currentUser.role || '').toLowerCase();
    const endpoint = role === 'interviewer' ? `/api/sessions/interviewer/${currentUser.id}` : `/api/sessions/interviewee/${currentUser.id}`;
    sessions = await api(endpoint);
    renderOverview();
    renderSessions('upcoming');
    renderInterviewerPanel();
    populateFeedbackSessions();
  } catch (err) {
    toast(err.message, 'error');
  }
}

function renderOverview() {
  const upcoming = sessions.filter(item => ['PENDING', 'CONFIRMED'].includes((item.status || '').toUpperCase()));
  const completed = sessions.filter(item => (item.status || '').toUpperCase() === 'COMPLETED');
  document.getElementById('stat-upcoming').textContent = upcoming.length;
  document.getElementById('stat-completed').textContent = completed.length;
  document.getElementById('stat-rating').textContent = Number(currentUser.averageRating || 0).toFixed(1);
  document.getElementById('stat-streak').textContent = Math.min(7, completed.length);
  document.getElementById('upcoming-list').innerHTML = upcoming.slice(0, 4).map(renderSessionCard).join('') || emptyState('No upcoming sessions.');
  renderSkillProgress();
}

function renderSessions(mode = 'upcoming') {
  document.querySelectorAll('.session-tabs .chip').forEach((chip, index) => chip.classList.toggle('active', (mode === 'upcoming') === (index === 0)));
  const list = mode === 'upcoming'
    ? sessions.filter(item => ['PENDING', 'CONFIRMED'].includes((item.status || '').toUpperCase()))
    : sessions.filter(item => ['COMPLETED', 'CANCELLED'].includes((item.status || '').toUpperCase()));
  document.getElementById('sessions-list').innerHTML = list.map(renderSessionCard).join('') || emptyState('No sessions here yet.');
}

function renderSessionCard(session) {
  const status = (session.status || 'PENDING').toUpperCase();
  const isInterviewer = (currentUser.role || '').toLowerCase() === 'interviewer';
  const canConfirm = isInterviewer && status === 'PENDING';
  const canComplete = status === 'CONFIRMED';
  return `
    <article class="session-card">
      <div class="session-card-head"><span class="badge badge-${statusClass(status)}">${status}</span><span>${countdown(session.startTime || session.scheduledAt)}</span></div>
      <h3>${esc(session.interviewType || session.title || session.topic || 'Interview')}</h3>
      <p>${fmtDate(session.startTime || session.scheduledAt)}</p>
      <p class="muted">Meeting status: ${esc(session.meetingStatus || 'ready')}</p>
      <div class="session-actions">
        ${session.meetingLink ? `<a class="btn btn-primary btn-sm" href="${esc(session.meetingLink)}" target="_blank" rel="noreferrer">Join Meeting</a>` : ''}
        ${canConfirm ? `<button class="btn btn-success btn-sm" onclick="updateSession('${session.id}','confirm')">Approve</button>` : ''}
        ${canComplete ? `<button class="btn btn-outline btn-sm" onclick="updateSession('${session.id}','complete')">Complete</button>` : ''}
        ${['PENDING', 'CONFIRMED'].includes(status) ? `<button class="btn btn-danger btn-sm" onclick="updateSession('${session.id}','cancel')">Cancel</button>` : ''}
      </div>
    </article>
  `;
}

async function updateSession(id, action) {
  try {
    await api(`/api/sessions/${id}/${action}`, { method: 'PATCH' });
    toast(`Session ${action}ed.`, 'success');
    await loadSessions();
    await loadNotifications();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function loadFeedback() {
  try {
    feedbackItems = await api('/api/feedback');
    const html = feedbackItems.slice(0, 6).map(renderFeedback).join('') || emptyState('No feedback yet.');
    document.getElementById('feedback-list').innerHTML = html;
    document.getElementById('recent-feedback').innerHTML = html;
  } catch {
    document.getElementById('feedback-list').innerHTML = emptyState('Feedback will appear here.');
    document.getElementById('recent-feedback').innerHTML = emptyState('Feedback will appear here.');
  }
}

function bindFeedbackForm() {
  document.getElementById('feedback-form').addEventListener('submit', async event => {
    event.preventDefault();
    const btn = document.getElementById('feedback-submit');
    btn.disabled = true;
    try {
      await api('/api/feedback', {
        method: 'POST',
        body: JSON.stringify({
          sessionId: val('fb-session'),
          reviewerId: currentUser.id,
          rating: Number(val('fb-rating')),
          communication: Number(val('fb-communication')),
          technicalSkills: Number(val('fb-technical')),
          comments: val('fb-comments'),
          strengths: val('fb-strengths'),
          weaknesses: val('fb-weaknesses'),
          recommendations: val('fb-recommendations'),
        }),
      });
      document.getElementById('feedback-form').reset();
      toast('Feedback submitted.', 'success');
      await loadFeedback();
    } catch (err) {
      showAlert('feedback-alert', err.message);
    } finally {
      btn.disabled = false;
    }
  });
}

function populateFeedbackSessions() {
  const select = document.getElementById('fb-session');
  const eligible = sessions.filter(item => ['CONFIRMED', 'COMPLETED'].includes((item.status || '').toUpperCase()));
  select.innerHTML = eligible.map(item => `<option value="${esc(item.id)}">${esc(item.interviewType || item.title || 'Interview')} - ${fmtDate(item.startTime)}</option>`).join('') || '<option value="">No eligible sessions</option>';
}

function renderFeedback(item) {
  return `
    <article class="feedback-item">
      <strong>${esc(String(item.rating || '-'))}/5</strong>
      <span>${esc(item.comments || '')}</span>
      ${item.recommendations ? `<small>${esc(item.recommendations)}</small>` : ''}
    </article>
  `;
}

function renderInterviewerPanel() {
  const incoming = sessions.filter(item => (item.status || '').toUpperCase() === 'PENDING');
  const incomingHost = document.getElementById('incoming-requests');
  if (incomingHost) incomingHost.innerHTML = incoming.map(renderSessionCard).join('') || emptyState('No pending requests.');
  const calendar = document.getElementById('calendar-view');
  if (calendar) calendar.innerHTML = sessions.slice(0, 8).map(item => `<div><strong>${new Date(item.startTime).toLocaleDateString()}</strong><span>${esc(item.interviewType || item.title || 'Interview')}</span></div>`).join('') || emptyState('Your calendar is clear.');
}

async function loadNotifications() {
  try {
    const data = await api(`/api/notifications?userId=${currentUser.id}`);
    document.getElementById('unread-badge').textContent = data.unread ? String(data.unread) : '';
    renderNotifications(data.items || []);
  } catch {
    renderNotifications([]);
  }
}

function renderNotifications(items) {
  const panel = document.getElementById('notification-panel');
  panel.innerHTML = items.map(item => `
    <button class="notification-item ${item.read ? '' : 'unread'}" onclick="markNotificationRead('${item.id}')">
      <strong>${esc(item.title || 'Notification')}</strong>
      <span>${esc(item.message || '')}</span>
    </button>
  `).join('') || emptyState('No notifications.');
}

function toggleNotifications() {
  document.getElementById('notification-panel').classList.toggle('open');
}

async function markNotificationRead(id) {
  await api(`/api/notifications/${id}/read`, { method: 'PATCH' });
  await loadNotifications();
}

function renderSkillProgress() {
  const skills = ['DSA', 'System Design', 'Communication', 'Backend'];
  document.getElementById('skill-progress').innerHTML = skills.map((skill, index) => {
    const value = Math.min(95, 36 + sessions.length * 8 + index * 9);
    return `<div><span>${skill}</span><div class="progress-track"><i style="width:${value}%"></i></div><strong>${value}%</strong></div>`;
  }).join('');
}

function defaultSlots() {
  const slots = [];
  for (let i = 1; i <= 6; i++) {
    const date = new Date();
    date.setDate(date.getDate() + i);
    date.setHours(i % 2 ? 10 : 17, i % 2 ? 30 : 0, 0, 0);
    slots.push(date.toISOString());
  }
  return slots;
}

function modal(html) {
  document.getElementById('modal-root').innerHTML = `<div class="modal-overlay" onclick="if(event.target === this) closeModal()"><div class="modal-card"><button class="icon-btn modal-close" onclick="closeModal()">×</button>${html}</div></div>`;
}

function closeModal() {
  document.getElementById('modal-root').innerHTML = '';
}

function toast(message, type = 'info') {
  const root = document.getElementById('toast-root');
  const item = document.createElement('div');
  item.className = `toast toast-${type}`;
  item.textContent = message;
  root.appendChild(item);
  setTimeout(() => item.remove(), 4200);
}

function showAlert(id, message, type = 'error') {
  const el = document.getElementById(id);
  el.textContent = message;
  el.className = `alert alert-${type} show`;
}

function val(id) {
  return document.getElementById(id)?.value?.trim() || '';
}

function initials(user) {
  const name = user.name || user.username || user.email || 'IP';
  return name.split(/\s+/).map(part => part[0]).join('').slice(0, 2).toUpperCase();
}

function fmtDate(value) {
  if (!value) return 'Flexible';
  try { return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }); } catch { return value; }
}

function countdown(value) {
  if (!value) return 'No time set';
  const diff = new Date(value).getTime() - Date.now();
  if (diff <= 0) return 'Now';
  const hours = Math.floor(diff / 36e5);
  const days = Math.floor(hours / 24);
  return days > 0 ? `${days}d left` : `${Math.max(1, hours)}h left`;
}

function statusClass(status) {
  return { PENDING: 'yellow', CONFIRMED: 'purple', COMPLETED: 'green', CANCELLED: 'red' }[status] || 'gray';
}

function skeletonCards(count) {
  return Array.from({ length: count }, () => '<div class="skeleton-card"></div>').join('');
}

function emptyState(text) {
  return `<div class="empty-state"><p>${esc(text)}</p></div>`;
}

function esc(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}
