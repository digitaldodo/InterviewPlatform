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
let activeWorkspace = 'INTERVIEWEE';
const ROUTES = new Set(['overview', 'discover', 'booking', 'sessions', 'feedback', 'notifications', 'profile', 'interviewer']);

window.addEventListener('DOMContentLoaded', async () => {
  currentUser = readJson('ip_user');
  if (!currentUser?.id) {
    window.location.href = '../index.html';
    return;
  }
  activeWorkspace = savedWorkspace();
  initUi();
  bindFilters();
  bindFeedbackForm();
  await Promise.all([loadSessions(), loadInterviewers(), loadRecommended(), loadFeedback(), loadNotifications()]);
  showSection(routeFromHash(), false);
});

window.addEventListener('hashchange', () => showSection(routeFromHash(), false));

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
  const roles = userRoles();
  if (!roles.includes(activeWorkspace)) activeWorkspace = roles[0] || 'INTERVIEWEE';
  currentUser.activeWorkspace = activeWorkspace;
  localStorage.setItem('ip_user', JSON.stringify(currentUser));
  const workspace = activeWorkspace.toLowerCase();
  document.getElementById('welcome-heading').textContent = `Welcome back, ${currentUser.name || currentUser.username || currentUser.email}`;
  document.getElementById('role-eyebrow').textContent = workspace === 'interviewer' ? 'Interviewer workspace' : 'Interviewee workspace';
  document.getElementById('sidebar-user-info').innerHTML = `<strong>${esc(currentUser.name || currentUser.username || 'User')}</strong><span>${esc(currentUser.email || '')}</span><span class="badge badge-purple">${esc(workspace)}</span>`;
  renderWorkspaceSwitcher(roles);
  applyWorkspaceVisibility();
  document.getElementById('primary-action').textContent = workspace === 'interviewer' ? 'View requests' : 'Book session';
  renderBookingStep();
}

function userRoles() {
  const roles = Array.isArray(currentUser.roles) && currentUser.roles.length ? currentUser.roles : [currentUser.role || 'INTERVIEWEE'];
  return [...new Set(roles.map(role => String(role || '').toUpperCase()).filter(role => ['INTERVIEWEE', 'INTERVIEWER'].includes(role)))];
}

function savedWorkspace() {
  const roles = userRoles();
  const saved = localStorage.getItem(`ip_workspace_${currentUser.id}`) || currentUser.activeWorkspace || currentUser.role || 'INTERVIEWEE';
  const normalized = String(saved).toUpperCase();
  return roles.includes(normalized) ? normalized : (roles[0] || 'INTERVIEWEE');
}

function renderWorkspaceSwitcher(roles) {
  const wrap = document.getElementById('workspace-switcher-wrap');
  const select = document.getElementById('workspace-switcher');
  if (!wrap || !select) return;
  wrap.style.display = roles.length > 1 ? 'grid' : 'none';
  select.innerHTML = roles.map(role => `<option value="${role}" ${role === activeWorkspace ? 'selected' : ''}>${workspaceLabel(role)}</option>`).join('');
}

function switchWorkspace(workspace) {
  const normalized = String(workspace || '').toUpperCase();
  if (!userRoles().includes(normalized)) return;
  activeWorkspace = normalized;
  currentUser.activeWorkspace = normalized;
  localStorage.setItem(`ip_workspace_${currentUser.id}`, normalized);
  localStorage.setItem('ip_user', JSON.stringify(currentUser));
  initUi();
  if (!routeAllowed(routeFromHash())) showSection('overview');
  loadSessions();
  loadRecommended();
  loadInterviewers();
}

function primaryWorkspaceAction() {
  showSection(activeWorkspace === 'INTERVIEWER' ? 'interviewer' : 'discover');
}

function workspaceLabel(role) {
  return role === 'INTERVIEWER' ? 'Interviewer Workspace' : 'Interviewee Workspace';
}

function applyWorkspaceVisibility() {
  const isInterviewer = activeWorkspace === 'INTERVIEWER';
  document.querySelectorAll('.interviewer-only').forEach(el => el.style.display = isInterviewer ? 'flex' : 'none');
  document.querySelectorAll('.interviewee-workspace').forEach(el => el.style.display = isInterviewer ? 'none' : '');
  document.body.classList.toggle('interviewer-workspace-active', isInterviewer);
}

function routeAllowed(route) {
  if (activeWorkspace === 'INTERVIEWER') return !['discover', 'booking'].includes(route);
  if (route === 'interviewer') return false;
  return true;
}

async function loadOwnProfile() {
  try {
    currentUser = await api('/api/users/me/profile');
    localStorage.setItem('ip_user', JSON.stringify(currentUser));
    initUi();
    renderProfile();
  } catch (err) {
    toast(err.message || 'Could not refresh profile.', 'error');
  }
}

function toggleSidebar(forceOpen) {
  const sidebar = document.getElementById('sidebar');
  if (typeof forceOpen === 'boolean') {
    sidebar.classList.toggle('open', forceOpen);
    return;
  }
  sidebar.classList.toggle('open');
}

function routeFromHash() {
  const value = window.location.hash.replace(/^#\/?/, '') || 'overview';
  return ROUTES.has(value) ? value : 'overview';
}

function showSection(name, updateRoute = true) {
  let targetName = ROUTES.has(name) ? name : 'overview';
  if (!routeAllowed(targetName)) targetName = 'overview';
  document.querySelectorAll('.dashboard-section').forEach(section => section.hidden = true);
  document.getElementById(`section-${targetName}`).hidden = false;
  document.querySelectorAll('.nav-link').forEach(link => link.classList.toggle('active', link.dataset.section === targetName));
  if (targetName === 'booking') renderBookingStep();
  if (targetName === 'feedback') populateFeedbackSessions();
  if (targetName === 'sessions') renderSessions('upcoming');
  if (targetName === 'notifications') loadNotifications();
  if (targetName === 'profile') renderProfile();
  document.querySelector('.topbar')?.classList.toggle('compact', targetName !== 'overview');
  if (window.innerWidth < 900) document.getElementById('sidebar').classList.remove('open');
  if (updateRoute && window.location.hash !== `#/${targetName}`) {
    history.pushState(null, '', `#/${targetName}`);
  }
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
  if (!grid) return;
  if (activeWorkspace === 'INTERVIEWER') {
    grid.innerHTML = emptyState('Switch to the interviewee workspace to browse interviewers.');
    return;
  }
  grid.innerHTML = skeletonCards(6);
  try {
    const params = new URLSearchParams({
      q: val('search-q'),
      expertise: val('filter-expertise'),
      company: val('filter-company'),
      language: val('filter-language'),
      sort: val('filter-sort'),
      excludeUserId: currentUser.id,
      page: interviewerPage,
      size: 9,
    });
    if (val('filter-rating')) params.set('minRating', val('filter-rating'));
    if (document.getElementById('filter-available').checked) params.set('available', 'true');
    if (document.getElementById('filter-free').checked) params.set('free', 'true');
    const page = await api(`/api/interviewers/search?${params.toString()}`);
    interviewers = filterSelf(page.items || []);
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
  if (!list) return;
  if (activeWorkspace === 'INTERVIEWER') {
    list.innerHTML = emptyState('Switch to the interviewee workspace to view recommendations.');
    return;
  }
  list.innerHTML = skeletonCards(3);
  try {
    const data = await api(`/api/interviewers/recommended?intervieweeId=${currentUser.id}`);
    const recommendations = filterSelf(data || []).slice(0, 4);
    list.innerHTML = recommendations.map(renderCompactInterviewer).join('') || emptyState('No other interviewers available yet.');
  } catch {
    list.innerHTML = emptyState('Recommendations will appear here.');
  }
}

function renderInterviewerGrid(list) {
  const grid = document.getElementById('interviewer-grid');
  list = filterSelf(list || []);
  if (!list.length) {
    grid.innerHTML = emptyState('No other interviewers available yet.');
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
      <div class="rating-row"><strong>${ratingLabel(interviewer)}</strong><span>${interviewer.reviewCount || 0} reviews</span><span>${interviewer.completedInterviews || 0} sessions</span></div>
      <div class="tag-row">${(interviewer.skills || []).slice(0, 4).map(skill => `<span>${esc(skill)}</span>`).join('')}</div>
      <p class="bio">${esc(interviewer.bio || 'No bio yet.')}</p>
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
        <div class="stats-inline"><span>${interviewer.yearsExperience || 0}+ years</span><span>${ratingSummary(interviewer)}</span><span>${interviewer.completedInterviews || 0} completed</span></div>
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
    const available = filterSelf(interviewers).slice(0, 6);
    host.innerHTML = `<h2>Choose interviewer</h2><div class="interviewer-grid compact">${available.map(item => `
      <article class="mini-card"><div class="avatar">${initials(item)}</div><strong>${esc(item.name || item.username || 'Interviewer')}</strong><small>${esc(item.currentRole || '')}</small><button class="btn btn-primary btn-sm" onclick="selectInterviewer('${item.id}')">Choose</button></article>
    `).join('') || emptyState('No other interviewers available yet.')}</div>`;
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
    const options = Array.isArray(slots) ? slots.filter(Boolean) : [];
    host.innerHTML = `<h2>Choose a slot</h2>${options.length
      ? `<div class="slot-grid">${options.map(slot => `<button onclick="chooseSlot(${jsArg(slot)})">${fmtDate(slot)}</button>`).join('')}</div>`
      : emptyState('No availability slots available yet.')}`;
  } catch {
    host.innerHTML = `<h2>Choose a slot</h2>${emptyState('No availability slots available yet.')}`;
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
    const endpoint = activeWorkspace === 'INTERVIEWER' ? `/api/sessions/interviewer/${currentUser.id}` : `/api/sessions/interviewee/${currentUser.id}`;
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
  document.getElementById('stat-rating').textContent = ratingValue(currentUser) || '-';
  const streakCard = document.getElementById('stat-streak-card');
  const hasPracticeStreak = Number.isFinite(Number(currentUser.practiceStreak));
  if (streakCard) {
    streakCard.hidden = !hasPracticeStreak;
    if (hasPracticeStreak) document.getElementById('stat-streak').textContent = String(Number(currentUser.practiceStreak));
  }
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
  const isInterviewer = activeWorkspace === 'INTERVIEWER';
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
  const html = items.map(item => `
    <button class="notification-item ${item.read ? '' : 'unread'}" onclick="markNotificationRead('${item.id}')">
      <strong>${esc(item.title || 'Notification')}</strong>
      <span>${esc(item.message || '')}</span>
    </button>
  `).join('') || emptyState('No notifications.');
  panel.innerHTML = html;
  const page = document.getElementById('notifications-list');
  if (page) page.innerHTML = html;
}

function toggleNotifications() {
  document.getElementById('notification-panel').classList.toggle('open');
}

async function markNotificationRead(id) {
  await api(`/api/notifications/${id}/read`, { method: 'PATCH' });
  await loadNotifications();
}

function renderProfile() {
  const summary = document.getElementById('profile-summary');
  if (!summary) return;
  const roles = userRoles().map(workspaceLabel).join(', ');
  const isVerified = Boolean(currentUser.isVerified);
  const profileCompletion = normalizedPercent(currentUser.profileCompletion ?? currentUser.profileCompletionPercent);
  summary.innerHTML = `
    <div class="preview-profile">
      <div class="avatar large">${currentUser.avatarUrl ? `<img src="${esc(currentUser.avatarUrl)}" alt="" style="width:100%;height:100%;object-fit:cover;border-radius:inherit;">` : initials(currentUser)}</div>
      <div>
        <h2>${esc(currentUser.name || currentUser.username || 'InterviewPrep user')}</h2>
        <p>${esc(currentUser.email || '')}</p>
        <div class="profile-status-row">
          <span class="badge badge-purple">${esc(roles)}</span>
          <span class="badge ${isVerified ? 'badge-green' : 'badge-yellow'}">${isVerified ? 'Verified' : 'Verification pending'}</span>
        </div>
      </div>
    </div>
    <div class="stats-inline" style="margin-top:1rem;">
      <span>${sessions.length} sessions</span>
      <span>${ratingSummary(currentUser)}</span>
      ${profileCompletion == null ? '' : `<span>${profileCompletion}% profile complete</span>`}
    </div>
    ${profileCompletion == null ? '' : `<div class="profile-completion"><div class="progress-track"><i style="width:${profileCompletion}%"></i></div></div>`}
    ${isVerified ? '' : `
      <div class="divider"></div>
      <div class="security-stack">
        <h2>Verify email</h2>
        <p>Enter the OTP sent to ${esc(currentUser.email || 'your email')} or request a fresh one.</p>
        <div class="form-grid">
          <div class="form-group">
            <label for="profile-otp">Verification OTP</label>
            <input id="profile-otp" inputmode="numeric" maxlength="6" placeholder="6-digit code" />
          </div>
          <div class="form-group">
            <label>&nbsp;</label>
            <div class="profile-actions-row">
              <button class="btn btn-primary" id="profile-verify-btn" onclick="verifyProfileOtp()">Verify Email</button>
              <button class="btn btn-outline" id="profile-resend-btn" onclick="resendProfileOtp()">Resend OTP</button>
            </div>
          </div>
        </div>
      </div>
    `}
    <div class="divider"></div>
    <form class="profile-form" onsubmit="saveProfile(event)">
      <h2>Edit profile</h2>
      <div class="form-grid">
        <div class="form-group">
          <label for="profile-name">Full name</label>
          <input id="profile-name" value="${esc(currentUser.name || currentUser.username || '')}" required />
        </div>
        <div class="form-group">
          <label for="profile-avatar">Avatar URL</label>
          <input id="profile-avatar" value="${esc(currentUser.avatarUrl || '')}" placeholder="https://..." />
        </div>
      </div>
      <div class="form-group">
        <label for="profile-avatar-file">Upload avatar preview</label>
        <input id="profile-avatar-file" type="file" accept="image/*" onchange="previewAvatarFile(event)" />
      </div>
      <div class="form-group">
        <label for="profile-bio">Bio/About</label>
        <textarea id="profile-bio" placeholder="Tell interviewers what you are preparing for">${esc(currentUser.bio || '')}</textarea>
      </div>
      <div class="form-grid">
        <div class="form-group">
          <label for="profile-skills">Skills</label>
          <input id="profile-skills" value="${esc((currentUser.skills || []).join(', '))}" placeholder="Java, DSA, System Design" />
        </div>
        <div class="form-group">
          <label for="profile-language">Languages</label>
          <input id="profile-language" value="${esc(currentUser.language || '')}" placeholder="English, Hindi" />
        </div>
      </div>
      <div class="form-grid">
        <div class="form-group">
          <label for="profile-domains">Preferred interview domains</label>
          <input id="profile-domains" value="${esc((currentUser.preferredDomains || []).join(', '))}" placeholder="Backend, Frontend, HR" />
        </div>
        <div class="form-group">
          <label for="profile-experience">Experience level</label>
          <select id="profile-experience">
            ${['', 'Student', 'Entry level', 'Mid level', 'Senior', 'Staff+'].map(level => `<option value="${esc(level)}" ${level === (currentUser.experienceLevel || '') ? 'selected' : ''}>${level || 'Select level'}</option>`).join('')}
          </select>
        </div>
        <div class="form-group">
          <label for="profile-availability">Availability slots</label>
          <input id="profile-availability" value="${esc((currentUser.availability || []).join(', '))}" placeholder="2026-05-10T10:30, Fridays 6 PM" />
        </div>
      </div>
      <button class="btn btn-primary btn-full" id="profile-save-btn">Save profile</button>
    </form>
    <div class="divider"></div>
    <form class="profile-form" onsubmit="changePassword(event)">
      <h2>Security</h2>
      <div class="form-grid">
        <div class="form-group">
          <label for="profile-current-password">Current password</label>
          <input id="profile-current-password" type="password" autocomplete="current-password" />
        </div>
        <div class="form-group">
          <label for="profile-new-password">New password</label>
          <input id="profile-new-password" type="password" autocomplete="new-password" />
        </div>
      </div>
      <button class="btn btn-outline btn-full" id="profile-password-btn">Change password</button>
    </form>
  `;
  const saved = document.getElementById('saved-interviewers');
  const ids = currentUser.favoriteInterviewerIds || [];
  const savedList = filterSelf(interviewers).filter(item => ids.includes(item.id));
  saved.innerHTML = savedList.map(renderCompactInterviewer).join('') || emptyState('Saved interviewers will appear here.');
  initProfileControls();
}

function initProfileControls() {
  FormUx.initTagInput('profile-skills', { placeholder: 'Add skill or expertise' });
  FormUx.initLanguageSelect('profile-language', { placeholder: 'Search languages' });
}

function filterSelf(list) {
  return (list || []).filter(item => item?.id !== currentUser.id);
}

async function resendProfileOtp() {
  const btn = document.getElementById('profile-resend-btn');
  setButtonLoading(btn, true, 'Sending');
  try {
    await api('/api/users/me/resend-otp', { method: 'POST' });
    toast('Verification OTP sent.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false);
  }
}

async function verifyProfileOtp() {
  const btn = document.getElementById('profile-verify-btn');
  setButtonLoading(btn, true, 'Verifying');
  try {
    const updated = await api('/api/users/me/verify-otp', {
      method: 'POST',
      body: JSON.stringify({ email: currentUser.email, otp: val('profile-otp') }),
    });
    currentUser = updated;
    localStorage.setItem('ip_user', JSON.stringify(updated));
    toast('Email verified.', 'success');
    renderProfile();
  } catch (err) {
    toast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false);
  }
}

async function saveProfile(event) {
  event.preventDefault();
  const btn = document.getElementById('profile-save-btn');
  setButtonLoading(btn, true, 'Saving');
  try {
    const updated = await api('/api/users/me/profile', {
      method: 'PUT',
      body: JSON.stringify({
        name: val('profile-name'),
        avatarUrl: val('profile-avatar'),
        bio: val('profile-bio'),
        skills: FormUx.getTagValues('profile-skills'),
        language: FormUx.getLanguageString('profile-language'),
        preferredDomains: splitList(val('profile-domains')),
        experienceLevel: val('profile-experience'),
        availability: splitList(val('profile-availability')),
      }),
    });
    currentUser = updated;
    localStorage.setItem('ip_user', JSON.stringify(updated));
    initUi();
    renderProfile();
    toast('Profile saved.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false);
  }
}

async function changePassword(event) {
  event.preventDefault();
  const btn = document.getElementById('profile-password-btn');
  setButtonLoading(btn, true, 'Updating');
  try {
    await api('/api/users/me/change-password', {
      method: 'POST',
      body: JSON.stringify({
        currentPassword: document.getElementById('profile-current-password').value,
        newPassword: document.getElementById('profile-new-password').value,
      }),
    });
    document.getElementById('profile-current-password').value = '';
    document.getElementById('profile-new-password').value = '';
    toast('Password updated.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  } finally {
    setButtonLoading(btn, false);
  }
}

function previewAvatarFile(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (file.size > 600_000) {
    toast('Please choose an image under 600 KB.', 'error');
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    document.getElementById('profile-avatar').value = reader.result;
  };
  reader.readAsDataURL(file);
}

function splitList(value) {
  return value.split(',').map(item => item.trim()).filter(Boolean);
}

function setButtonLoading(btn, loading, label = 'Working') {
  if (!btn) return;
  if (loading) {
    btn.disabled = true;
    btn.dataset.originalText = btn.textContent;
    btn.innerHTML = `<span class="spinner"></span> ${label}`;
  } else {
    btn.disabled = false;
    btn.textContent = btn.dataset.originalText || btn.textContent;
  }
}

function renderSkillProgress() {
  const host = document.getElementById('skill-progress');
  const progressData = getProgressData();
  if (!progressData || progressData.length === 0) {
    host.innerHTML = emptyState('No progress data available yet');
    return;
  }
  host.innerHTML = progressData.map(item => `
    <div>
      <span>${esc(item.label)}</span>
      <div class="progress-track"><i style="width:${item.percent}%"></i></div>
      <strong>${item.percent}%</strong>
    </div>
  `).join('');
}

function getProgressData() {
  const source = currentUser.progressData || currentUser.skillProgress || currentUser.analytics?.progress;
  const entries = Array.isArray(source)
    ? source
    : Object.entries(source || {}).map(([label, value]) => ({ label, value }));
  return entries
    .map(item => {
      const label = item.skill || item.name || item.label;
      const rawPercent = item.percent ?? item.percentage ?? item.progress ?? item.completion ?? item.value;
      const percent = normalizedPercent(rawPercent);
      if (!label || percent == null) return null;
      return { label: String(label), percent };
    })
    .filter(Boolean);
}

function normalizedPercent(value) {
  const percent = Number(value);
  if (!Number.isFinite(percent)) return null;
  return Math.max(0, Math.min(100, Math.round(percent)));
}

function ratingLabel(user) {
  return ratingValue(user) || 'No ratings yet';
}

function ratingValue(user) {
  const rating = Number(user?.averageRating);
  const hasRating = Number.isFinite(rating) && rating > 0 && Number(user?.reviewCount || 0) > 0;
  return hasRating ? rating.toFixed(1) : null;
}

function ratingSummary(user) {
  const label = ratingLabel(user);
  return label === 'No ratings yet' ? label : `${label} rating`;
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

function jsArg(value) {
  return esc(JSON.stringify(String(value)));
}
