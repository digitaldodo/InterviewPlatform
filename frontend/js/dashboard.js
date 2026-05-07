const API_BASE = window.INTERVIEW_API_BASE;

let currentUser = null;
let sessions = [];
let feedbackItems = [];
let interviewers = [];
let interviewerDirectory = new Map();
let meetingProviders = [];
let interviewerPage = 0;
let interviewerTotalPages = 1;
let selectedInterviewer = null;
let bookingStep = 1;
let searchTimer = null;
let activeWorkspace = 'INTERVIEWEE';
let activeMeetingSession = null;
let activeMeetingAccess = null;
let activeMeetingTimer = null;
let jitsiApi = null;
let jitsiScriptPromise = null;
let meetingUiState = { audioMuted: false, videoMuted: false, screenSharing: false, participants: 1, joined: false };
let availabilitySchedules = [];
let generatedAvailabilitySlots = [];
let availabilityEditId = null;
let availabilityLoading = false;
let availabilityError = '';
let profileAvatarFile = null;
let profileAvatarPreviewUrl = '';
const DEFAULT_MEETING_PROVIDERS = [
  { key: 'JITSI', label: 'In-platform meeting', embedded: true, enabled: true, isDefault: true },
];
const AVAILABILITY_DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const AVAILABILITY_DURATIONS = [15, 30, 45, 60, 90, 120];
const TOPIC_OPTIONS = ['Java', 'DSA', 'Spring Boot', 'System Design', 'React', 'Node.js', 'SQL', 'Frontend', 'Backend', 'Behavioral', 'Resume Review'];
const DOMAIN_SUGGESTIONS = ['Backend', 'Frontend', 'Full Stack', 'DevOps', 'HR', 'DSA', 'System Design', 'Java', 'React', 'Spring Boot'];
const AVAILABILITY_PREFERENCES = [
  'Weekday mornings',
  'Weekday afternoons',
  'Weekday evenings',
  'Weekend only',
  'Flexible schedule',
  'Late night availability',
  'Early morning availability',
];
const ROUTES = new Set(['overview', 'discover', 'booking', 'sessions', 'meeting', 'feedback', 'notifications', 'profile', 'interviewer']);
const PROFILE_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/avif']);
let bookingState = createBookingState();

function createBookingState() {
  return {
    interviewer: null,
    interviewerQuery: '',
    selectedDate: '',
    selectedSlotStart: '',
    slotOptions: [],
    hiddenSlotStarts: [],
    topics: [],
    interviewType: '',
    notes: '',
    meetingProvider: preferredMeetingProvider(),
    confirmedSession: null,
    slotLoading: false,
    slotError: '',
  };
}

function preferredMeetingProvider() {
  return meetingProviders.find(item => item.isDefault)?.key || meetingProviders[0]?.key || DEFAULT_MEETING_PROVIDERS[0].key;
}

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
  await Promise.all([loadMeetingProviders(), loadSessions(), loadInterviewers(), loadRecommended(), loadFeedback(), loadNotifications(), loadAvailabilityManagement()]);
  showSection(routeFromHash(), false);
});

window.addEventListener('hashchange', () => showSection(routeFromHash(), false));
window.addEventListener('resize', handleResponsiveShell);
document.addEventListener('keydown', handleGlobalKeydown);

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
  document.getElementById('sidebar-user-info').innerHTML = `
    <div class="sidebar-profile-card">
      ${avatarMarkup(currentUser)}
      <div class="sidebar-profile-copy">
        <strong>${esc(currentUser.name || currentUser.username || 'User')}</strong>
        <span>${esc(currentUser.email || '')}</span>
        <span class="badge badge-purple">${esc(workspace)}</span>
      </div>
    </div>
  `;
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

function hasInterviewerRole() {
  return userRoles().includes('INTERVIEWER');
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
  loadAvailabilityManagement();
}

function primaryWorkspaceAction() {
  showSection(activeWorkspace === 'INTERVIEWER' ? 'interviewer' : 'booking');
}

function openAvailabilityManager() {
  if (!hasInterviewerRole()) return;
  if (activeWorkspace !== 'INTERVIEWER') {
    switchWorkspace('INTERVIEWER');
  }
  showSection('interviewer');
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
    renderAvailabilityPanels();
  } catch (err) {
    toast(err.message || 'Could not refresh profile.', 'error');
  }
}

function toggleSidebar(forceOpen) {
  const sidebar = document.getElementById('sidebar');
  if (typeof forceOpen === 'boolean') {
    sidebar.classList.toggle('open', forceOpen);
    syncShellState();
    return;
  }
  sidebar.classList.toggle('open');
  syncShellState();
}

function routeFromHash() {
  const value = window.location.hash.replace(/^#\/?/, '') || 'overview';
  return ROUTES.has(value) ? value : 'overview';
}

function showSection(name, updateRoute = true) {
  let targetName = ROUTES.has(name) ? name : 'overview';
  if (!routeAllowed(targetName)) targetName = 'overview';
  const meetingWasVisible = !document.getElementById('section-meeting').hidden;
  if (meetingWasVisible && targetName !== 'meeting') destroyMeetingFrame();
  document.querySelectorAll('.dashboard-section').forEach(section => section.hidden = true);
  document.getElementById(`section-${targetName}`).hidden = false;
  document.querySelectorAll('.nav-link').forEach(link => link.classList.toggle('active', link.dataset.section === targetName));
  document.querySelectorAll('.bottom-nav button').forEach(button => {
    const isActive = button.dataset.section === targetName;
    button.classList.toggle('active', isActive);
    if (isActive) {
      button.setAttribute('aria-current', 'page');
    } else {
      button.removeAttribute('aria-current');
    }
  });
  if (targetName === 'booking') renderBookingStep();
  if (targetName === 'meeting' && !activeMeetingSession) renderMeetingPlaceholder();
  if (targetName === 'feedback') populateFeedbackSessions();
  if (targetName === 'sessions') renderSessions('upcoming');
  if (targetName === 'notifications') loadNotifications();
  if (targetName === 'profile') renderProfile();
  document.querySelector('.topbar')?.classList.toggle('compact', targetName !== 'overview');
  if (window.innerWidth < 900) document.getElementById('sidebar').classList.remove('open');
  syncShellState();
  if (updateRoute && window.location.hash !== `#/${targetName}`) {
    history.pushState(null, '', `#/${targetName}`);
  }
}

function handleResponsiveShell() {
  if (window.innerWidth >= 761) {
    document.getElementById('sidebar')?.classList.remove('open');
  }
  syncShellState();
}

function handleGlobalKeydown(event) {
  if (event.key !== 'Escape') return;
  if (document.getElementById('modal-root')?.children.length) {
    closeModal();
    return;
  }
  if (document.getElementById('sidebar')?.classList.contains('open')) {
    toggleSidebar(false);
  }
}

function syncShellState() {
  const sidebarOpen = Boolean(document.getElementById('sidebar')?.classList.contains('open')) && window.innerWidth < 761;
  const modalOpen = Boolean(document.getElementById('modal-root')?.children.length);
  document.body.classList.toggle('shell-locked', sidebarOpen || modalOpen);
}

async function api(path, options = {}, retry = true) {
  const token = localStorage.getItem('ip_access_token');
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const headers = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };
  if (!isFormData && !headers['Content-Type'] && !headers['content-type']) {
    headers['Content-Type'] = 'application/json';
  }
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
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

function rememberInterviewers(list) {
  filterSelf(list || []).forEach(item => {
    if (item?.id) interviewerDirectory.set(item.id, item);
  });
}

function refreshSessionSurfaces() {
  if (document.getElementById('upcoming-list')) renderOverview();
  if (document.getElementById('sessions-list') && !document.getElementById('section-sessions')?.hidden) {
    renderSessions('upcoming');
  }
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
    rememberInterviewers(interviewers);
    interviewerTotalPages = Math.max(1, page.totalPages || 1);
    renderInterviewerGrid(interviewers);
    refreshSessionSurfaces();
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
    rememberInterviewers(recommendations);
    list.innerHTML = recommendations.map(renderCompactInterviewer).join('') || interviewerEmptyState(
      'No interviewer recommendations yet',
      'We will show recommended interviewers here as soon as matching profiles are available.'
    );
    refreshSessionSurfaces();
  } catch {
    list.innerHTML = interviewerEmptyState(
      'Recommendations are unavailable right now',
      'Try refreshing in a moment or browse all interviewers from search.'
    );
  }
}

function renderInterviewerGrid(list) {
  const grid = document.getElementById('interviewer-grid');
  list = filterSelf(list || []);
  if (!list.length) {
    grid.innerHTML = interviewerEmptyState(
      'No interviewers available right now',
      'Check back shortly or widen your search filters to see more profiles.'
    );
    return;
  }
  grid.classList.remove('skeleton-grid');
  grid.innerHTML = list.map(renderInterviewerCard).join('');
}

function renderInterviewerCard(interviewer) {
  const skills = Array.isArray(interviewer.skills) ? interviewer.skills.slice(0, 5) : [];
  return `
    <article class="interviewer-card">
      <div class="interviewer-card-main">
        <div class="interviewer-avatar-shell">
          ${avatarMarkup(interviewer, 'avatar avatar-profile')}
        </div>
        <div class="interviewer-card-copy">
          <div class="interviewer-card-title-row">
            <div class="interviewer-card-title">
              <h3>${esc(interviewerName(interviewer))}</h3>
              <p>${esc(interviewerRole(interviewer))}</p>
              <span>${esc(interviewerCompany(interviewer))}</span>
            </div>
            <button class="icon-btn favorite-btn" title="Save favorite" aria-label="Save ${esc(interviewerName(interviewer))}" onclick="favoriteInterviewer('${interviewer.id}')">♡</button>
          </div>
          <div class="rating-row interviewer-stats">
            <strong>${ratingLabel(interviewer)}</strong>
            <span>${Number(interviewer.reviewCount || 0)} reviews</span>
            <span>${Number(interviewer.completedInterviews || 0)} sessions</span>
          </div>
          <div class="tag-row">${skills.map(skill => `<span>${esc(skill)}</span>`).join('') || '<span>Interview coaching</span>'}</div>
        </div>
      </div>
      <p class="bio interviewer-bio">${esc(bioPreview(interviewer.bio))}</p>
      <div class="interviewer-card-footer">
        <span class="availability-pill ${interviewer.acceptingBookings === false ? 'is-muted' : ''}">${esc(availabilityLabel(interviewer))}</span>
        <div class="card-actions">
          <button class="btn btn-outline btn-sm" onclick="openProfile('${interviewer.id}')">Profile</button>
          <button class="btn btn-primary btn-sm" onclick="selectInterviewer('${interviewer.id}')">Book</button>
        </div>
      </div>
    </article>
  `;
}

function renderCompactInterviewer(interviewer) {
  return `
    <button class="mini-interviewer" onclick="selectInterviewer('${interviewer.id}')">
      ${avatarMarkup(interviewer, 'avatar avatar-compact')}
      <span class="mini-interviewer-copy">
        <strong>${esc(interviewerName(interviewer))}</strong>
        <small>${esc(interviewerRole(interviewer))} · ${esc(availabilityLabel(interviewer))}</small>
      </span>
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
    selectedInterviewer = interviewer;
    rememberInterviewers([interviewer]);
    modal(`
      <div class="profile-modal">
        ${avatarMarkup(interviewer, 'avatar large')}
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
  selectedInterviewer = filterSelf(interviewers).find(item => item.id === id) || filterSelf([interviewerDirectory.get(id)])[0] || null;
  if (!selectedInterviewer || selectedInterviewer.id === currentUser.id) {
    toast('That interviewer is not available for booking.', 'error');
    return;
  }
  bookingState = {
    ...createBookingState(),
    interviewer: selectedInterviewer,
    topics: selectedBookingTopics(),
    interviewType: bookingState.interviewType,
    meetingProvider: bookingState.meetingProvider || preferredMeetingProvider(),
  };
  bookingStep = 2;
  showSection('booking');
}

function renderBookingStep() {
  const host = document.getElementById('booking-step-content');
  if (!host) return;
  if (!bookingState.interviewer && bookingStep > 1) bookingStep = 1;
  if (!bookingState.selectedDate && bookingStep > 2) bookingStep = 2;
  if (!bookingState.selectedSlotStart && bookingStep > 3 && !bookingState.confirmedSession) bookingStep = 3;
  document.querySelectorAll('.step').forEach(step => step.classList.toggle('active', Number(step.dataset.step) === bookingStep));
  if (bookingStep === 1) {
    host.innerHTML = renderBookingInterviewerStep();
  }
  if (bookingStep === 2) {
    renderBookingDateStep();
  }
  if (bookingStep === 3) renderBookingSlotStep();
  if (bookingStep === 4) {
    host.innerHTML = bookingState.confirmedSession ? renderBookingConfirmationStep() : renderBookingConfirmStep();
  }
}

function renderBookingInterviewerStep() {
  const available = filterBookingInterviewers();
  return `
    <div class="booking-stage">
      <div class="booking-stage-head">
        <div>
          <h2>Search interviewer</h2>
          <p class="availability-muted">Pick from live interviewer profiles without leaving the dashboard.</p>
        </div>
      </div>
      <div class="booking-search-row">
        <input id="booking-interviewer-search" type="search" placeholder="Search by name, skill, role, or company" value="${esc(bookingState.interviewerQuery)}" oninput="setBookingInterviewerSearch(this.value)" />
        <button class="btn btn-outline btn-sm" type="button" onclick="showSection('discover')">Open full search</button>
      </div>
      <div class="interviewer-grid compact">
        ${available.map(renderBookingInterviewerCard).join('') || interviewerEmptyState(
          bookingState.interviewerQuery ? 'No interviewers match that search' : 'No interviewers available right now',
          bookingState.interviewerQuery
            ? 'Try a broader search by role, company, or skill.'
            : 'Once interviewer profiles are available, you can book directly from this screen.'
        )}
      </div>
    </div>
  `;
}

function renderBookingInterviewerCard(interviewer) {
  const skills = Array.isArray(interviewer.skills) ? interviewer.skills.slice(0, 3) : [];
  return `
    <article class="mini-card booking-interviewer-card">
      <div class="booking-card-profile">
        ${avatarMarkup(interviewer, 'avatar avatar-booking')}
        <div class="booking-card-copy">
          <strong>${esc(interviewerName(interviewer))}</strong>
          <small>${esc(interviewerRole(interviewer))}</small>
          <span class="booking-card-meta">${esc(interviewerCompany(interviewer))}</span>
        </div>
      </div>
      <div class="rating-row">
        <strong>${ratingLabel(interviewer)}</strong>
        <span>${Number(interviewer.reviewCount || 0)} reviews</span>
      </div>
      <div class="tag-row">${skills.map(skill => `<span>${esc(skill)}</span>`).join('') || '<span>Interview coaching</span>'}</div>
      <p class="bio">${esc(bioPreview(interviewer.bio, 118))}</p>
      <div class="booking-card-actions">
        <span class="availability-pill ${interviewer.acceptingBookings === false ? 'is-muted' : ''}">${esc(availabilityLabel(interviewer))}</span>
        <button class="btn btn-primary btn-sm" onclick="selectInterviewer('${interviewer.id}')">Select</button>
      </div>
    </article>
  `;
}

function setBookingInterviewerSearch(value) {
  bookingState.interviewerQuery = String(value || '').trim();
  renderBookingStep();
}

async function renderBookingDateStep() {
  const host = document.getElementById('booking-step-content');
  if (!host) return;
  const interviewer = bookingState.interviewer || {};
  const dayGroups = groupedBookingSlots();
  host.innerHTML = `
    <div class="booking-stage">
      <div class="booking-stage-head">
        <div>
          <h2>Select date</h2>
          <p class="availability-muted">${esc(interviewer.name || interviewer.username || 'Selected interviewer')} has real generated slots for the next two weeks.</p>
        </div>
        <button class="btn btn-outline btn-sm" type="button" onclick="goToBookingStep(1)">Change interviewer</button>
      </div>
      ${renderBookingSelectionBanner()}
      ${bookingState.slotLoading
        ? `<div class="booking-date-grid">${skeletonCards(4)}</div>`
        : bookingState.slotError
          ? `${emptyState(bookingState.slotError)}<div class="booking-flow-actions"><button class="btn btn-outline btn-sm" type="button" onclick="refreshBookingAvailability(true)">Retry</button></div>`
          : dayGroups.length
            ? `<div class="booking-date-grid">${dayGroups.map(group => `
                <button class="booking-date-card ${group.key === bookingState.selectedDate ? 'active' : ''}" type="button" onclick="selectBookingDate('${group.key}')">
                  <strong>${esc(group.dayLabel)}</strong>
                  <span>${esc(group.dateLabel)}</span>
                  <small>${group.availableCount} open${group.bookedCount ? ` • ${group.bookedCount} booked` : ''}</small>
                </button>
              `).join('')}</div>`
            : slotEmptyState(emptyBookingMessage())}
    </div>
  `;
  if (!bookingState.slotLoading && !bookingState.slotOptions.length && !bookingState.slotError) {
    ensureBookingSlotsLoaded();
  }
}

async function loadMeetingProviders() {
  try {
    const providers = await api('/api/sessions/meeting-providers');
    meetingProviders = (providers || []).filter(item => item.enabled);
    if (!meetingProviders.length) throw new Error('No meeting providers available');
  } catch {
    meetingProviders = DEFAULT_MEETING_PROVIDERS.slice();
  }
  bookingState.meetingProvider = bookingState.meetingProvider || preferredMeetingProvider();
  if (document.getElementById('booking-step-content')) renderBookingStep();
}

async function renderBookingSlotStep() {
  const host = document.getElementById('booking-step-content');
  if (!host) return;
  const interviewer = bookingState.interviewer || {};
  const selectedDay = groupedBookingSlots().find(group => group.key === bookingState.selectedDate);
  host.innerHTML = `
    <div class="booking-stage">
      <div class="booking-stage-head">
        <div>
          <h2>Select generated slot</h2>
          <p class="availability-muted">${esc(interviewer.name || interviewer.username || 'Selected interviewer')} • ${esc(selectedDay?.dateLabel || 'Pick a date')}</p>
        </div>
        <button class="btn btn-outline btn-sm" type="button" onclick="goToBookingStep(2)">Change date</button>
      </div>
      ${renderBookingSelectionBanner()}
      ${bookingState.slotLoading
        ? `<div class="slot-grid">${skeletonCards(4)}</div>`
        : bookingState.slotError
          ? `${emptyState(bookingState.slotError)}<div class="booking-flow-actions"><button class="btn btn-outline btn-sm" type="button" onclick="refreshBookingAvailability(true)">Retry</button></div>`
          : selectedDay
            ? selectedDay.availableCount === 0
              ? slotEmptyState('No open slots remain on this date. Choose another day to keep booking.')
              : `
              <div class="booking-slot-day-card">
                <div class="booking-slot-day-head">
                  <div>
                    <strong>${esc(selectedDay.dayLabel)}</strong>
                    <p>${esc(selectedDay.dateLabel)}</p>
                  </div>
                  <span class="badge badge-green">${selectedDay.availableCount} open</span>
                </div>
                <div class="slot-grid booking-slot-grid">
                  ${selectedDay.slots.map(slot => `
                    <button
                      class="booking-slot-card ${slot.status === 'BOOKED' ? 'is-booked' : ''}"
                      type="button"
                      ${slot.status === 'BOOKED' ? 'disabled' : ''}
                      onclick="chooseSlotByStart('${slot.startTime}')"
                    >
                      <strong>${esc(formatSlotTime(slot.startTime))}</strong>
                      <span>${esc(formatSlotRange(slot.startTime, slot.endTime))}</span>
                      <small>${slot.status === 'BOOKED' ? 'Booked' : `${slot.durationMinutes || 45} min`}</small>
                    </button>
                  `).join('')}
                </div>
              </div>
            `
            : slotEmptyState('Choose a date to see available interview slots.')}
    </div>
  `;
  if (!bookingState.slotLoading && !bookingState.slotOptions.length && !bookingState.slotError) {
    ensureBookingSlotsLoaded();
  }
}

function setBookingProvider(provider) {
  bookingState.meetingProvider = provider || bookingState.meetingProvider || 'JITSI';
}

function renderMeetingProviderField() {
  const providers = meetingProviders.length ? meetingProviders : DEFAULT_MEETING_PROVIDERS;
  const current = bookingState.meetingProvider || providers.find(item => item.isDefault)?.key || providers[0]?.key || 'JITSI';
  bookingState.meetingProvider = current;
  return `
    <div class="form-group">
      <label for="booking-meeting-provider">Meeting format</label>
      <select id="booking-meeting-provider" onchange="setBookingProvider(this.value)">
        ${providers.map(provider => `<option value="${esc(provider.key)}" ${provider.key === current ? 'selected' : ''}>${esc(provider.label)}</option>`).join('')}
      </select>
    </div>
  `;
}

function renderBookingConfirmStep() {
  const slot = selectedBookingSlot();
  return `
    <div class="booking-stage">
      <div class="booking-stage-head">
        <div>
          <h2>Confirm booking</h2>
          <p class="availability-muted">Review the slot, choose one or more interview topics, and submit the request.</p>
        </div>
        <button class="btn btn-outline btn-sm" type="button" onclick="goToBookingStep(3)">Change slot</button>
      </div>
      ${renderBookingSelectionBanner()}
      <div class="confirm-box">
        <p><strong>Date</strong><span>${esc(formatLongDate(bookingState.selectedDate))}</span></p>
        <p><strong>Slot</strong><span>${esc(slot ? formatDateTimeRange(slot.startTime, slot.endTime) : 'Select a slot')}</span></p>
        <p><strong>Duration</strong><span>${esc(String(slot?.durationMinutes || 45))} min</span></p>
      </div>
      <div class="form-group">
        <label>Interview topics</label>
        <div class="type-grid booking-type-grid">
          ${TOPIC_OPTIONS.map(topic => `<button class="type-card ${selectedBookingTopics().includes(topic) ? 'active' : ''}" type="button" onclick="toggleBookingTopic('${topic}')">${topic}</button>`).join('')}
        </div>
        <input id="booking-custom-topic" class="topic-custom-input" type="text" placeholder="Add another topic, e.g. Graphs" onkeydown="handleCustomTopicKey(event)" />
        <div class="topic-chip-row">${topicTags(selectedBookingTopics())}</div>
      </div>
      ${renderMeetingProviderField()}
      <div class="form-group">
        <label for="booking-notes">Goals or context</label>
        <textarea id="booking-notes" placeholder="Optional goals or context" oninput="setBookingNotes(this.value)">${esc(bookingState.notes)}</textarea>
      </div>
      <button class="btn btn-primary btn-full sticky-action" onclick="confirmBooking()">Confirm booking</button>
    </div>
  `;
}

function renderBookingConfirmationStep() {
  const session = bookingState.confirmedSession;
  return `
    <div class="booking-stage">
      <div class="booking-stage-head">
        <div>
          <h2>Confirmation</h2>
          <p class="availability-muted">Your booking is saved and the session is now in your dashboard.</p>
        </div>
      </div>
      <div class="booking-success-card">
        ${bookingState.interviewer ? `
          <div class="session-participant-row">
            ${avatarMarkup(bookingState.interviewer, 'avatar avatar-compact')}
            <div>
              <strong>${esc(interviewerName(bookingState.interviewer))}</strong>
              <span>${esc(interviewerMetaLine(bookingState.interviewer))}</span>
            </div>
          </div>
        ` : ''}
        <span class="badge badge-${statusClass((session?.status || 'PENDING').toUpperCase())}">${esc((session?.status || 'PENDING').toUpperCase())}</span>
        <h3>${esc(sessionTitle(session) || 'Interview request sent')}</h3>
        <div class="topic-chip-row">${topicTags(sessionTopics(session))}</div>
        <p>${esc(fmtDate(session?.startTime))}</p>
        <small>${esc(providerLabel(session?.meetingProvider))} meeting prepared${session?.joinUrl ? ' and linked to your session.' : '.'}</small>
      </div>
      <div class="booking-flow-actions">
        <button class="btn btn-primary" type="button" onclick="showSection('sessions')">View sessions</button>
        <button class="btn btn-outline" type="button" onclick="startAnotherBooking()">Book another</button>
      </div>
    </div>
  `;
}

function selectedBookingTopics() {
  return FormUx.normalizeValues(bookingState.topics?.length ? bookingState.topics : splitList(bookingState.interviewType || ''));
}

function toggleBookingTopic(topic) {
  const topics = selectedBookingTopics();
  const exists = topics.some(item => item.toLowerCase() === String(topic).toLowerCase());
  bookingState.topics = exists ? topics.filter(item => item.toLowerCase() !== String(topic).toLowerCase()) : [...topics, topic];
  bookingState.interviewType = bookingState.topics.join(', ');
  renderBookingStep();
}

function handleCustomTopicKey(event) {
  if (event.key !== 'Enter' && event.key !== ',') return;
  event.preventDefault();
  const value = event.currentTarget.value.trim();
  if (!value) return;
  bookingState.topics = FormUx.normalizeValues([...selectedBookingTopics(), value]);
  bookingState.interviewType = bookingState.topics.join(', ');
  renderBookingStep();
}

function setBookingNotes(value) {
  bookingState.notes = String(value || '');
}

function goToBookingStep(step) {
  bookingStep = Math.max(1, Math.min(4, Number(step) || 1));
  renderBookingStep();
}

function selectBookingDate(dateKey) {
  bookingState.selectedDate = dateKey;
  bookingState.selectedSlotStart = '';
  bookingState.confirmedSession = null;
  bookingStep = 3;
  renderBookingStep();
}

function chooseSlotByStart(startTime) {
  const slot = bookingState.slotOptions.find(item => item.startTime === startTime);
  if (!slot || slot.status !== 'AVAILABLE') {
    toast('That slot is no longer available.', 'error');
    return;
  }
  bookingState.selectedSlotStart = slot.startTime;
  bookingState.confirmedSession = null;
  bookingStep = 4;
  renderBookingStep();
}

function selectedBookingSlot() {
  return bookingState.slotOptions.find(item => item.startTime === bookingState.selectedSlotStart) || null;
}

async function ensureBookingSlotsLoaded(force = false) {
  if (!bookingState.interviewer?.id || (bookingState.slotLoading && !force)) return;
  if (!force && bookingState.slotOptions.length) return;
  bookingState.slotLoading = true;
  bookingState.slotError = '';
  renderBookingStep();
  try {
    const slots = await api(`/api/interviewers/${bookingState.interviewer.id}/slots?days=14&includeUnavailable=true`);
    bookingState.slotOptions = normalizeBookingSlots(slots);
    const groups = groupedBookingSlots();
    if (!bookingState.selectedDate || !groups.some(group => group.key === bookingState.selectedDate)) {
      bookingState.selectedDate = groups[0]?.key || '';
    }
    const selectedStillAvailable = bookingState.slotOptions.some(slot => slot.startTime === bookingState.selectedSlotStart && slot.status === 'AVAILABLE');
    if (!selectedStillAvailable) bookingState.selectedSlotStart = '';
  } catch (err) {
    bookingState.slotOptions = [];
    bookingState.selectedDate = '';
    bookingState.selectedSlotStart = '';
    bookingState.slotError = err.message || 'Could not load generated slots right now.';
  } finally {
    bookingState.slotLoading = false;
    renderBookingStep();
  }
}

async function refreshBookingAvailability(force = false) {
  bookingState.slotOptions = force ? [] : bookingState.slotOptions;
  await ensureBookingSlotsLoaded(true);
}

function normalizeBookingSlots(slots) {
  const hiddenStarts = new Set(bookingState.hiddenSlotStarts || []);
  return (Array.isArray(slots) ? slots : [])
    .filter(slot => slot?.startTime && !hiddenStarts.has(slot.startTime))
    .map(slot => ({
      ...slot,
      status: String(slot.status || 'AVAILABLE').toUpperCase(),
      durationMinutes: Number(slot.durationMinutes) || 45,
    }))
    .sort((left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime());
}

function groupedBookingSlots() {
  const groups = new Map();
  bookingState.slotOptions.forEach(slot => {
    const key = bookingDateKey(slot.startTime);
    if (!key) return;
    if (!groups.has(key)) {
      const date = new Date(slot.startTime);
      groups.set(key, {
        key,
        dayLabel: date.toLocaleDateString(undefined, { weekday: 'long' }),
        dateLabel: date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }),
        slots: [],
        availableCount: 0,
        bookedCount: 0,
      });
    }
    const group = groups.get(key);
    group.slots.push(slot);
    if (slot.status === 'BOOKED') group.bookedCount += 1;
    else group.availableCount += 1;
  });
  return [...groups.values()];
}

function bookingDateKey(value) {
  try {
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) return '';
    return date.toISOString().slice(0, 10);
  } catch {
    return '';
  }
}

function filterBookingInterviewers() {
  const term = bookingState.interviewerQuery.toLowerCase();
  return filterSelf(interviewers).filter(item => {
    if (!term) return true;
    const haystack = [
      item.name,
      item.username,
      item.currentRole,
      item.company,
      ...(Array.isArray(item.skills) ? item.skills : []),
    ].filter(Boolean).join(' ').toLowerCase();
    return haystack.includes(term);
  }).slice(0, 8);
}

function renderBookingSelectionBanner() {
  const interviewer = bookingState.interviewer;
  if (!interviewer) return '';
  const label = interviewer.name || interviewer.username || 'Selected interviewer';
  return `
    <div class="booking-selection-banner">
      ${avatarMarkup(interviewer, 'avatar avatar-compact')}
      <div>
        <strong>${esc(label)}</strong>
        <p>${esc(interviewerMetaLine(interviewer))}</p>
      </div>
    </div>
  `;
}

function emptyBookingMessage() {
  if (bookingState.interviewer && bookingState.interviewer.acceptingBookings === false) {
    return 'This interviewer is not accepting bookings right now.';
  }
  return 'No available interview slots have been published for this interviewer yet.';
}

function formatSlotTime(value) {
  try {
    return new Date(value).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  } catch {
    return String(value || '');
  }
}

function formatSlotRange(startTime, endTime) {
  if (!startTime) return 'Unavailable';
  try {
    const end = endTime ? new Date(endTime) : null;
    return end ? `${formatSlotTime(startTime)} - ${formatSlotTime(end)}` : formatSlotTime(startTime);
  } catch {
    return formatSlotTime(startTime);
  }
}

function formatLongDate(value) {
  if (!value) return 'Select a date';
  try {
    return new Date(value).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
  } catch {
    return String(value);
  }
}

async function confirmBooking() {
  const slot = selectedBookingSlot();
  if (!bookingState.interviewer?.id || bookingState.interviewer.id === currentUser.id) {
    toast('You cannot book yourself as interviewer.', 'error');
    bookingStep = 1;
    renderBookingStep();
    return;
  }
  if (!slot || slot.status !== 'AVAILABLE') {
    toast('That slot is no longer available.', 'error');
    bookingStep = 3;
    renderBookingStep();
    return;
  }
  const topics = selectedBookingTopics();
  if (!topics.length) {
    toast('Choose at least one interview topic before confirming.', 'error');
    return;
  }
  try {
    const session = await api('/api/bookings', {
      method: 'POST',
      body: JSON.stringify({
        interviewerId: bookingState.interviewer.id,
        intervieweeId: currentUser.id,
        interviewType: topics.join(', '),
        topics,
        startTime: slot.startTime,
        durationMinutes: slot.durationMinutes,
        notes: bookingState.notes.trim(),
        meetingProvider: document.getElementById('booking-meeting-provider')?.value || bookingState.meetingProvider || 'JITSI',
      }),
    });
    toast('Booking saved. Your session appears in the dashboard now.', 'success');
    sessions.unshift(session);
    bookingState.hiddenSlotStarts = [...new Set([...(bookingState.hiddenSlotStarts || []), slot.startTime])];
    bookingState.slotOptions = bookingState.slotOptions.filter(item => item.startTime !== slot.startTime);
    const groups = groupedBookingSlots();
    if (!groups.some(group => group.key === bookingState.selectedDate)) {
      bookingState.selectedDate = groups[0]?.key || '';
    }
    bookingState.selectedSlotStart = '';
    bookingState.confirmedSession = session;
    bookingStep = 4;
    await loadSessions();
    renderBookingStep();
  } catch (err) {
    toast(err.message, 'error');
  }
}

function startAnotherBooking() {
  bookingState.confirmedSession = null;
  bookingState.selectedSlotStart = '';
  bookingState.topics = [];
  bookingState.interviewType = '';
  bookingState.notes = '';
  bookingStep = bookingState.selectedDate ? 3 : 2;
  renderBookingStep();
}

async function loadSessions() {
  try {
    const endpoint = activeWorkspace === 'INTERVIEWER' ? `/api/sessions/interviewer/${currentUser.id}` : `/api/sessions/interviewee/${currentUser.id}`;
    sessions = sortSessions(await api(endpoint));
    if (activeMeetingSession?.id) {
      activeMeetingSession = sessions.find(item => item.id === activeMeetingSession.id) || activeMeetingSession;
      if (!document.getElementById('section-meeting').hidden) renderMeetingMeta(activeMeetingAccess || activeMeetingSession);
    }
    renderOverview();
    renderSessions('upcoming');
    renderInterviewerPanel();
    populateFeedbackSessions();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function loadAvailabilityManagement(showErrors = false) {
  if (!hasInterviewerRole()) {
    availabilitySchedules = [];
    generatedAvailabilitySlots = [];
    availabilityEditId = null;
    availabilityError = '';
    availabilityLoading = false;
    renderAvailabilityPanels();
    return;
  }
  availabilityLoading = true;
  availabilityError = '';
  renderAvailabilityPanels();
  try {
    const [schedules, slots] = await Promise.all([
      api('/api/interviewer-availability/me'),
      api(`/api/interviewers/${currentUser.id}/slots?days=14`),
    ]);
    availabilitySchedules = Array.isArray(schedules) ? schedules : [];
    generatedAvailabilitySlots = Array.isArray(slots) ? slots : [];
  } catch (err) {
    availabilitySchedules = [];
    generatedAvailabilitySlots = [];
    availabilityError = err.message || 'Could not load availability right now.';
    if (showErrors) toast(availabilityError, 'error');
  } finally {
    availabilityLoading = false;
    renderAvailabilityPanels();
  }
}

function renderAvailabilityPanels() {
  renderInterviewerAvailabilityPanel();
  renderProfileAvailabilityPanel();
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
  const list = sortSessions(mode === 'upcoming'
    ? sessions.filter(item => ['PENDING', 'CONFIRMED'].includes((item.status || '').toUpperCase()))
    : sessions.filter(item => ['COMPLETED', 'CANCELLED'].includes((item.status || '').toUpperCase())));
  document.getElementById('sessions-list').innerHTML = list.map(renderSessionCard).join('') || emptyState('No sessions here yet.');
}

function renderSessionCard(session) {
  const status = (session.status || 'PENDING').toUpperCase();
  const meetingStatus = normalizeMeetingStatus(session);
  const isInterviewer = activeWorkspace === 'INTERVIEWER';
  const canConfirm = isInterviewer && status === 'PENDING';
  const canComplete = status === 'CONFIRMED';
  const joinLabel = currentUser.id === session.interviewerId && meetingStatus !== 'LIVE' ? 'Start Meeting' : 'Join Meeting';
  const hasMeeting = Boolean(session.meetingId || session.joinUrl || session.meetingLink || session.meetingProvider);
  const summary = feedbackSummaryForSession(session.id);
  const participant = sessionParticipant(session);
  return `
    <article class="session-card">
      <div class="session-card-head"><span class="badge badge-${statusClass(status)}">${status}</span><span>${countdown(session.startTime || session.scheduledAt)}</span></div>
      <h3>${esc(sessionTitle(session))}</h3>
      <div class="session-participant-row">
        ${avatarMarkup(participant, 'avatar avatar-compact')}
        <div>
          <strong>${esc(participant.name || participant.username || 'Interviewer')}</strong>
          <span>${esc(interviewerMetaLine(participant))}</span>
        </div>
      </div>
      <div class="topic-chip-row">${topicTags(sessionTopics(session))}</div>
      <p>${fmtDate(session.startTime || session.scheduledAt)}</p>
      <div class="session-meta-row">
        <span class="badge badge-${meetingStatusClass(meetingStatus)}">${meetingStatusText(meetingStatus)}</span>
        <span>${esc(providerLabel(session.meetingProvider))}</span>
      </div>
      ${summary ? `<div class="feedback-summary"><strong>${esc(summary.rating)}/5 feedback</strong><span>${esc(summary.text)}</span></div>` : ''}
      <div class="session-actions">
        ${hasMeeting ? `<button class="btn btn-primary btn-sm" onclick="openMeeting('${session.id}')">${joinLabel}</button>` : ''}
        ${canConfirm ? `<button class="btn btn-success btn-sm" onclick="updateSession('${session.id}','confirm')">Approve</button>` : ''}
        ${canComplete ? `<button class="btn btn-outline btn-sm" onclick="updateSession('${session.id}','complete')">Complete</button>` : ''}
        ${['PENDING', 'CONFIRMED'].includes(status) ? `<button class="btn btn-danger btn-sm" onclick="updateSession('${session.id}','cancel')">Cancel</button>` : ''}
      </div>
    </article>
  `;
}

function sessionParticipant(session) {
  const isInterviewer = activeWorkspace === 'INTERVIEWER';
  if (!isInterviewer) {
    return interviewerDirectory.get(session?.interviewerId) || {
      id: session?.interviewerId,
      name: session?.interviewerName || session?.hostName || 'Interviewer',
      currentRole: session?.interviewerRole || 'Interview coach',
      company: session?.interviewerCompany,
      avatarUrl: session?.interviewerAvatarUrl,
    };
  }
  return {
    id: session?.candidateId || session?.intervieweeId,
    name: session?.candidateName || session?.intervieweeName || 'Interviewee',
    currentRole: session?.candidateRole || session?.intervieweeRole || 'Candidate',
    company: session?.candidateCompany || session?.intervieweeCompany,
    avatarUrl: session?.candidateAvatarUrl || session?.intervieweeAvatarUrl,
  };
}

function sessionTopics(session) {
  if (!session) return [];
  return FormUx.normalizeValues(Array.isArray(session?.topics) && session.topics.length
    ? session.topics
    : splitList(session?.interviewType || session?.title || session?.topic || 'Interview'));
}

function sessionTitle(session) {
  const topics = sessionTopics(session);
  return topics.length ? topics.join(', ') : (session?.interviewType || session?.title || session?.topic || 'Interview');
}

function topicTags(topics) {
  return FormUx.normalizeValues(topics).map(topic => `<span>${esc(topic)}</span>`).join('');
}

function feedbackSummaryForSession(sessionId) {
  const item = feedbackItems.find(feedback => feedback.sessionId === sessionId);
  if (!item) return null;
  return {
    rating: String(item.rating || '-'),
    text: item.improvementAreas || item.recommendations || item.comments || 'Feedback submitted',
  };
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
    renderOverview();
    renderSessions('upcoming');
    renderInterviewerPanel();
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
      const topicFeedback = collectTopicFeedback();
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
          improvementAreas: val('fb-recommendations'),
          recommendations: val('fb-recommendations'),
          topicFeedback,
        }),
      });
      document.getElementById('feedback-form').reset();
      renderFeedbackTopicSections();
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
  select.innerHTML = eligible.map(item => `<option value="${esc(item.id)}">${esc(sessionTitle(item))} - ${fmtDate(item.startTime)}</option>`).join('') || '<option value="">No eligible sessions</option>';
  renderFeedbackTopicSections();
}

function renderFeedback(item) {
  const topics = item.topicFeedback || [];
  return `
    <article class="feedback-item">
      <div class="feedback-item-head"><strong>${esc(String(item.rating || '-'))}/5</strong>${topics.length ? `<div class="topic-chip-row">${topicTags(topics.map(topic => topic.topic))}</div>` : ''}</div>
      <span>${esc(item.comments || '')}</span>
      ${topics.length ? `<div class="feedback-topic-summary">${topics.map(topic => `<span>${esc(topic.topic)}${topic.rating ? `: ${esc(String(topic.rating))}/5` : ''}</span>`).join('')}</div>` : ''}
      ${item.improvementAreas || item.recommendations ? `<small>${esc(item.improvementAreas || item.recommendations)}</small>` : ''}
    </article>
  `;
}

function renderFeedbackTopicSections() {
  const host = document.getElementById('feedback-topic-sections');
  if (!host) return;
  const session = sessions.find(item => item.id === val('fb-session'));
  const topics = sessionTopics(session);
  if (!topics.length) {
    host.innerHTML = '';
    return;
  }
  host.innerHTML = `
    <div class="feedback-topic-head">
      <strong>Topic-specific feedback</strong>
      <div class="topic-chip-row">${topicTags(topics)}</div>
    </div>
    ${topics.map((topic, index) => renderFeedbackTopicSection(topic, index)).join('')}
  `;
}

function renderFeedbackTopicSection(topic, index) {
  const skills = suggestedSkillsForTopic(topic);
  return `
    <details class="topic-feedback-card" open>
      <summary><span>${esc(topic)}</span><small>Rate sub-skills and examples</small></summary>
      <div class="form-grid">
        <div class="form-group"><label for="fb-topic-rating-${index}">Topic rating</label><select id="fb-topic-rating-${index}" data-topic="${esc(topic)}"><option value="">Optional</option><option>5</option><option>4</option><option>3</option><option>2</option><option>1</option></select></div>
        <div class="form-group"><label for="fb-topic-examples-${index}">Examples observed</label><input id="fb-topic-examples-${index}" placeholder="Specific signals or moments" /></div>
      </div>
      ${skills.length ? `<div class="skill-rating-grid">${skills.map(skill => `
        <label class="skill-rating-row"><span>${esc(skill)}</span><select data-topic-skill="${esc(topic)}" data-skill="${esc(skill)}"><option value="">-</option><option>5</option><option>4</option><option>3</option><option>2</option><option>1</option></select></label>
      `).join('')}</div>` : ''}
      <div class="form-grid">
        <div class="form-group"><label for="fb-topic-strengths-${index}">Topic strengths</label><input id="fb-topic-strengths-${index}" placeholder="Strong areas" /></div>
        <div class="form-group"><label for="fb-topic-weaknesses-${index}">Topic weaknesses</label><input id="fb-topic-weaknesses-${index}" placeholder="Gaps to practice" /></div>
      </div>
      <div class="form-group"><label for="fb-topic-comments-${index}">Topic comments</label><textarea id="fb-topic-comments-${index}" placeholder="Actionable topic-specific notes"></textarea></div>
    </details>
  `;
}

function suggestedSkillsForTopic(topic) {
  const key = String(topic || '').toLowerCase();
  if (key.includes('java')) return ['OOP understanding', 'Collections', 'Streams', 'Multithreading'];
  if (key.includes('dsa')) return ['Arrays', 'Trees', 'DP', 'Graphs'];
  if (key.includes('system')) return ['Scalability', 'API design', 'Database modeling'];
  if (key.includes('react')) return ['Components', 'State management', 'Hooks', 'Rendering performance'];
  if (key.includes('sql')) return ['Joins', 'Indexes', 'Query optimization', 'Data modeling'];
  if (key.includes('spring')) return ['Dependency injection', 'REST APIs', 'Persistence', 'Testing'];
  return [];
}

function collectTopicFeedback() {
  const session = sessions.find(item => item.id === val('fb-session'));
  return sessionTopics(session).map((topic, index) => {
    const skillRatings = {};
    document.querySelectorAll('[data-topic-skill]').forEach(select => {
      if (select.dataset.topicSkill !== topic) return;
      if (select.value) skillRatings[select.dataset.skill] = Number(select.value);
    });
    return {
      topic,
      rating: Number(val(`fb-topic-rating-${index}`) || 0),
      skillRatings,
      examples: val(`fb-topic-examples-${index}`),
      strengths: val(`fb-topic-strengths-${index}`),
      weaknesses: val(`fb-topic-weaknesses-${index}`),
      comments: val(`fb-topic-comments-${index}`),
    };
  }).filter(item => item.rating || Object.keys(item.skillRatings).length || item.examples || item.strengths || item.weaknesses || item.comments);
}

function renderInterviewerPanel() {
  const incoming = sessions.filter(item => (item.status || '').toUpperCase() === 'PENDING');
  const incomingHost = document.getElementById('incoming-requests');
  if (incomingHost) incomingHost.innerHTML = incoming.map(renderSessionCard).join('') || emptyState('No pending requests.');
  const calendar = document.getElementById('calendar-view');
  if (calendar) calendar.innerHTML = sessions.slice(0, 8).map(item => `<div><strong>${new Date(item.startTime).toLocaleDateString()}</strong><span>${esc(sessionTitle(item))}</span></div>`).join('') || emptyState('Your calendar is clear.');
  renderInterviewerAvailabilityPanel();
}

function renderInterviewerAvailabilityPanel() {
  const host = document.getElementById('availability-management-panel');
  if (!host) return;
  if (!hasInterviewerRole()) {
    host.hidden = true;
    host.innerHTML = '';
    return;
  }
  host.hidden = false;
  if (availabilityLoading) {
    host.innerHTML = `
      <div class="panel-head">
        <div><h2>Availability management</h2><p class="availability-muted">Weekly schedule and generated slots.</p></div>
      </div>
      <div class="availability-shell">${skeletonCards(1)}${skeletonCards(2)}</div>
    `;
    return;
  }
  const editing = availabilitySchedules.find(item => item.id === availabilityEditId) || null;
  const formValues = {
    dayOfWeek: editing?.dayOfWeek || 'MONDAY',
    startTime: editing?.startTime || '09:00',
    endTime: editing?.endTime || '17:00',
    durationMinutes: editing?.durationMinutes || 60,
  };
  const scheduleContent = availabilityError
    ? emptyState(availabilityError)
    : availabilitySchedules.length
      ? `<div class="availability-list">${availabilitySchedules.map(item => renderAvailabilityItem(item, true)).join('')}</div>`
      : `<div class="availability-summary-empty">${emptyState('This interviewer has not added availability yet.')}</div>`;
  const slotsContent = availabilityError
    ? emptyState(availabilityError)
    : generatedAvailabilitySlots.length
      ? `<div class="availability-slot-list">${generatedAvailabilitySlots.slice(0, 8).map(renderGeneratedSlotItem).join('')}</div>`
      : `<div class="availability-summary-empty">${emptyState(availabilitySchedules.length ? 'No upcoming generated slots are available yet.' : 'This interviewer has not added availability yet.')}</div>`;
  host.innerHTML = `
    <div class="panel-head">
      <div>
        <h2>Availability management</h2>
        <p class="availability-muted">Set recurring weekly schedules, then let the backend generate bookable slots automatically.</p>
      </div>
      <button class="btn btn-outline btn-sm" type="button" onclick="resetAvailabilityForm()">${editing ? 'Cancel edit' : 'New availability'}</button>
    </div>
    <div class="availability-shell">
      <div class="availability-form-card">
        <form class="availability-form" onsubmit="saveAvailability(event)">
          <div>
            <h3>${editing ? 'Edit weekly schedule' : 'Add weekly schedule'}</h3>
            <p class="availability-summary-note">Choose a day, time window, and interview duration. Slots are generated from real API data only.</p>
          </div>
          <div class="form-group">
            <label for="availability-day">Day</label>
            <select id="availability-day">
              ${AVAILABILITY_DAYS.map(day => `<option value="${day}" ${day === formValues.dayOfWeek ? 'selected' : ''}>${esc(formatDayLabel(day))}</option>`).join('')}
            </select>
          </div>
          <div class="form-grid">
            <div class="form-group">
              <label for="availability-start">Start time</label>
              <input id="availability-start" type="time" value="${esc(formValues.startTime)}" required />
            </div>
            <div class="form-group">
              <label for="availability-end">End time</label>
              <input id="availability-end" type="time" value="${esc(formValues.endTime)}" required />
            </div>
          </div>
          <div class="form-group">
            <label for="availability-duration">Interview duration</label>
            <select id="availability-duration">
              ${AVAILABILITY_DURATIONS.map(minutes => `<option value="${minutes}" ${Number(minutes) === Number(formValues.durationMinutes) ? 'selected' : ''}>${minutes} minutes</option>`).join('')}
            </select>
          </div>
          <div class="availability-form-actions">
            <button class="btn btn-primary" id="availability-save-btn">${editing ? 'Save changes' : 'Add availability'}</button>
            ${editing ? '<button class="btn btn-outline" type="button" onclick="resetAvailabilityForm()">Cancel</button>' : ''}
          </div>
        </form>
      </div>
      <div class="availability-preview-grid">
        <div class="availability-content-card">
          <div class="availability-heading-row">
            <div>
              <h3>Recurring schedules</h3>
              <p class="availability-summary-note">Weekly windows currently available for booking.</p>
            </div>
            <span class="badge badge-purple">${availabilitySchedules.length} saved</span>
          </div>
          ${scheduleContent}
        </div>
        <div class="availability-slot-card">
          <div class="availability-heading-row">
            <div>
              <h3>Upcoming generated slots</h3>
              <p class="availability-summary-note">Pulled from the backend scheduling engine for the next 14 days.</p>
            </div>
            <span class="badge badge-green">${generatedAvailabilitySlots.length} open</span>
          </div>
          ${slotsContent}
        </div>
      </div>
    </div>
  `;
}

function renderProfileAvailabilityPanel() {
  const host = document.getElementById('profile-availability-panel');
  if (!host) return;
  if (!hasInterviewerRole()) {
    host.hidden = true;
    host.innerHTML = '';
    return;
  }
  host.hidden = false;
  if (availabilityLoading) {
    host.innerHTML = `
      <div class="panel-head">
        <div><h2>Availability schedule</h2><p class="availability-muted">Syncing interviewer availability.</p></div>
        <button class="btn btn-outline btn-sm" type="button" onclick="openAvailabilityManager()">Manage schedule</button>
      </div>
      <div class="availability-summary-stack">${skeletonCards(2)}</div>
    `;
    return;
  }
  const summaryContent = availabilityError
    ? emptyState(availabilityError)
    : availabilitySchedules.length
      ? `<div class="availability-summary-stack">${availabilitySchedules.map(item => renderAvailabilityItem(item, false)).join('')}</div>`
      : `<div class="availability-summary-empty">${emptyState('This interviewer has not added availability yet.')}</div>`;
  const nextSlots = availabilityError
    ? ''
    : generatedAvailabilitySlots.length
      ? `
        <div class="availability-summary-card">
          <h3>Next generated slots</h3>
          <div class="availability-slot-list">
            ${generatedAvailabilitySlots.slice(0, 4).map(renderGeneratedSlotItem).join('')}
          </div>
        </div>
      `
      : '';
  host.innerHTML = `
    <div class="panel-head">
      <div>
        <h2>Availability schedule</h2>
        <p class="availability-muted">Keep your weekly interview hours current without leaving your profile.</p>
      </div>
      <button class="btn btn-outline btn-sm" type="button" onclick="openAvailabilityManager()">Manage schedule</button>
    </div>
    ${summaryContent}
    ${nextSlots}
  `;
}

function renderAvailabilityItem(item, includeActions) {
  const actionButtons = includeActions ? `
    <div class="availability-item-actions">
      <button class="btn btn-outline btn-sm" type="button" onclick="startAvailabilityEdit('${item.id}')">Edit</button>
      <button class="btn btn-danger btn-sm" type="button" onclick="deleteAvailability('${item.id}')">Delete</button>
    </div>
  ` : '';
  return `
    <article class="availability-item">
      <div class="availability-heading-row">
        <strong>${esc(formatDayLabel(item.dayOfWeek))}</strong>
        <span class="badge badge-gray">${esc(String(item.durationMinutes || 0))} min</span>
      </div>
      <div class="availability-meta">
        <span>${esc(formatPlainTime(item.startTime))} - ${esc(formatPlainTime(item.endTime))}</span>
        <span>${esc(formatWindowSummary(item.startTime, item.endTime, item.durationMinutes))}</span>
      </div>
      ${actionButtons}
    </article>
  `;
}

function renderGeneratedSlotItem(slot) {
  return `
    <article class="availability-slot-item">
      <strong>${esc(formatShortDate(slot.startTime))}</strong>
      <span>${esc(formatDateTimeRange(slot.startTime, slot.endTime))}</span>
      <small>${esc(String(slot.durationMinutes || 0))} min slot</small>
    </article>
  `;
}

function startAvailabilityEdit(id) {
  availabilityEditId = id;
  openAvailabilityManager();
  renderAvailabilityPanels();
}

function resetAvailabilityForm() {
  availabilityEditId = null;
  renderAvailabilityPanels();
}

async function saveAvailability(event) {
  event.preventDefault();
  const btn = document.getElementById('availability-save-btn');
  const editing = Boolean(availabilityEditId);
  const payload = {
    dayOfWeek: val('availability-day'),
    startTime: val('availability-start'),
    endTime: val('availability-end'),
    durationMinutes: Number(val('availability-duration')),
  };
  if (!payload.dayOfWeek || !payload.startTime || !payload.endTime || !payload.durationMinutes) {
    toast('Please complete all availability fields.', 'error');
    return;
  }
  if (payload.endTime <= payload.startTime) {
    toast('End time must be later than start time.', 'error');
    return;
  }
  setButtonLoading(btn, true, availabilityEditId ? 'Saving' : 'Adding');
  try {
    await api(availabilityEditId ? `/api/interviewer-availability/${availabilityEditId}` : '/api/interviewer-availability', {
      method: availabilityEditId ? 'PUT' : 'POST',
      body: JSON.stringify(payload),
    });
    availabilityEditId = null;
    toast(editing ? 'Availability updated.' : 'Availability added.', 'success');
    await loadAvailabilityManagement(true);
  } catch (err) {
    toast(err.message || 'Could not save availability.', 'error');
  } finally {
    setButtonLoading(btn, false);
  }
}

async function deleteAvailability(id) {
  if (!id) return;
  if (!window.confirm('Remove this weekly availability block?')) return;
  try {
    await api(`/api/interviewer-availability/${id}`, { method: 'DELETE' });
    if (availabilityEditId === id) availabilityEditId = null;
    toast('Availability deleted.', 'success');
    await loadAvailabilityManagement(true);
  } catch (err) {
    toast(err.message || 'Could not delete availability.', 'error');
  }
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
  const showLegacyAvailabilityField = !hasInterviewerRole();
  const availabilityPreference = intervieweeAvailabilityPreference();
  const availabilityNotes = intervieweeAvailabilityNotes();
  summary.innerHTML = `
    <div class="preview-profile">
      <div id="profile-summary-avatar">${avatarMarkup(currentUser, 'avatar large', currentProfileAvatarUrl())}</div>
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
      <div class="form-group">
        <label for="profile-name">Full name</label>
        <input id="profile-name" value="${esc(currentUser.name || currentUser.username || '')}" required />
      </div>
      <div class="profile-avatar-upload">
        <div class="profile-avatar-preview-card" id="profile-avatar-preview">
          ${profileAvatarPreviewCard()}
        </div>
        <div class="form-group">
          <label for="profile-avatar-file">Profile photo / logo / avatar / DP</label>
          <input id="profile-avatar-file" type="file" accept="image/jpeg,image/png,image/webp,image/gif,image/avif" onchange="previewAvatarFile(event)" />
        </div>
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
          <small class="field-hint">Press Enter or comma to add a domain. Suggestions and custom domains are both supported.</small>
        </div>
        <div class="form-group">
          <label for="profile-experience">Experience level</label>
          <select id="profile-experience">
            ${['', 'Student', 'Entry level', 'Mid level', 'Senior', 'Staff+'].map(level => `<option value="${esc(level)}" ${level === (currentUser.experienceLevel || '') ? 'selected' : ''}>${level || 'Select level'}</option>`).join('')}
          </select>
        </div>
      </div>
      ${showLegacyAvailabilityField ? `
        <div class="availability-preference-card">
          <h3>Interviewee availability preferences</h3>
          <p class="availability-summary-note">These preferences help recommendations and booking context. Interviewers still control the actual schedulable slots.</p>
          <div class="form-grid">
            <div class="form-group">
              <label for="profile-availability-preference">Preferred time window</label>
              <select id="profile-availability-preference">
                <option value="">Select preference</option>
                ${AVAILABILITY_PREFERENCES.map(option => `<option value="${esc(option)}" ${option === availabilityPreference ? 'selected' : ''}>${esc(option)}</option>`).join('')}
              </select>
            </div>
            <div class="form-group">
              <label for="profile-availability-days">Preferred days</label>
              <select id="profile-availability-days">
                ${['', 'Weekdays', 'Weekends', 'Any day'].map(option => `<option value="${esc(option)}" ${option === intervieweePreferredDays() ? 'selected' : ''}>${esc(option || 'Select days')}</option>`).join('')}
              </select>
            </div>
          </div>
          <div class="form-group">
            <label for="profile-availability-notes">Additional availability notes</label>
            <textarea id="profile-availability-notes" class="compact-textarea" placeholder="Available after office hours, prefer IST timezone, flexible on weekends">${esc(availabilityNotes)}</textarea>
          </div>
        </div>
      ` : `
        <div class="availability-summary-card">
          <h3>Weekly availability</h3>
          <p class="availability-summary-note">Manage recurring interviewer schedules from the interviewer workspace. Your generated slots update automatically there.</p>
          <div class="availability-form-actions">
            <button class="btn btn-outline btn-sm" type="button" onclick="openAvailabilityManager()">Open availability manager</button>
          </div>
        </div>
      `}
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
  saved.innerHTML = savedList.map(renderCompactInterviewer).join('') || interviewerEmptyState(
    'No saved interviewers yet',
    'Profiles you save for later will appear here for quick booking.'
  );
  initProfileControls();
  FormUx.initPasswordToggles(summary);
  renderProfileAvailabilityPanel();
}

function initProfileControls() {
  FormUx.initTagInput('profile-skills', { placeholder: 'Add skill or expertise' });
  FormUx.initTagInput('profile-domains', {
    placeholder: 'Add domain',
    label: 'Add preferred interview domain',
    suggestions: DOMAIN_SUGGESTIONS,
    commitOnTab: false,
    suggestionClickCommits: false,
  });
  FormUx.initLanguageSelect('profile-language', { placeholder: 'Search languages' });
}

function intervieweeAvailabilityValues() {
  return Array.isArray(currentUser.availability) ? currentUser.availability.filter(Boolean) : [];
}

function intervieweeAvailabilityPreference() {
  return intervieweeAvailabilityValues().find(item => AVAILABILITY_PREFERENCES.includes(item)) || '';
}

function intervieweePreferredDays() {
  return intervieweeAvailabilityValues().find(item => ['Weekdays', 'Weekends', 'Any day'].includes(item)) || '';
}

function intervieweeAvailabilityNotes() {
  return intervieweeAvailabilityValues()
    .filter(item => !AVAILABILITY_PREFERENCES.includes(item))
    .filter(item => !['Weekdays', 'Weekends', 'Any day'].includes(item))
    .join(', ');
}

function profileAvailabilityPayload() {
  return [
    val('profile-availability-preference'),
    val('profile-availability-days'),
    val('profile-availability-notes'),
  ].filter(Boolean);
}

function filterSelf(list) {
  return (list || []).filter(item => item?.id !== currentUser.id);
}

function interviewerEmptyState(title, message) {
  return `
    <div class="empty-state empty-state-rich">
      <strong>${esc(title)}</strong>
      <p>${esc(message)}</p>
    </div>
  `;
}

function slotEmptyState(message) {
  return `
    <div class="empty-state empty-state-rich">
      <strong>No slots available</strong>
      <p>${esc(message)}</p>
    </div>
  `;
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

async function openMeeting(sessionId) {
  const session = sessions.find(item => item.id === sessionId);
  if (!session) {
    toast('Session not found.', 'error');
    return;
  }
  activeMeetingSession = session;
  activeMeetingAccess = null;
  meetingUiState = { audioMuted: false, videoMuted: false, screenSharing: false, participants: 1, joined: false };
  showSection('meeting');
  renderMeetingLoading(session);
  try {
    const isHost = currentUser.id === session.interviewerId;
    const shouldStart = isHost && normalizeMeetingStatus(session) !== 'LIVE';
    const access = await api(
      shouldStart ? `/api/sessions/${sessionId}/meeting/start` : `/api/sessions/${sessionId}/meeting-access`,
      shouldStart ? { method: 'POST' } : {},
    );
    activeMeetingAccess = access;
    syncSession({
      ...session,
      meetingProvider: access.meetingProvider,
      meetingId: access.meetingId,
      meetingStatus: access.meetingStatus,
      joinUrl: access.joinUrl || session.joinUrl || session.meetingLink,
      hostUrl: access.hostUrl || session.hostUrl,
      meetingStartedAt: access.meetingStartedAt || session.meetingStartedAt,
      meetingEndedAt: access.meetingEndedAt || session.meetingEndedAt,
    });
    renderOverview();
    renderSessions('upcoming');
    renderInterviewerPanel();
    await renderMeetingRoom(access);
  } catch (err) {
    renderMeetingError(err.message || 'Could not open meeting.');
    toast(err.message || 'Could not open meeting.', 'error');
  }
}

function renderMeetingPlaceholder() {
  renderMeetingSummary({
    title: 'Interview room',
    subtitle: 'Select an upcoming session to join or start the meeting.',
    providerLabel: 'Meeting',
    meetingStatus: 'SCHEDULED',
  });
  document.getElementById('meeting-meta').innerHTML = emptyState('Choose a scheduled session to see meeting details.');
  const fallback = document.getElementById('meeting-fallback');
  const embed = document.getElementById('meeting-embed-root');
  document.getElementById('meeting-toolbar').hidden = true;
  document.getElementById('meeting-open-external').hidden = true;
  embed.innerHTML = '';
  fallback.hidden = false;
  fallback.innerHTML = `<div class="meeting-fallback-card"><h3>No meeting selected</h3><p>Open a session card from your dashboard to launch the room.</p></div>`;
  resetMeetingTimer();
}

function renderMeetingLoading(session) {
  renderMeetingSummary({
    title: sessionTitle(session),
    subtitle: `Preparing ${providerLabel(session.meetingProvider)} access for ${fmtDate(session.startTime)}`,
    providerLabel: providerLabel(session.meetingProvider),
    meetingStatus: normalizeMeetingStatus(session),
  });
  document.getElementById('meeting-meta').innerHTML = skeletonCards(2);
  const fallback = document.getElementById('meeting-fallback');
  fallback.hidden = false;
  fallback.innerHTML = `<div class="meeting-fallback-card"><h3>Preparing your room</h3><p>Loading secure access and meeting controls.</p></div>`;
  document.getElementById('meeting-embed-root').innerHTML = '';
  document.getElementById('meeting-toolbar').hidden = true;
  resetMeetingTimer();
}

async function renderMeetingRoom(access) {
  renderMeetingSummary({
    title: access.sessionTitle || sessionTitle(activeMeetingSession) || 'Interview session',
    subtitle: `${fmtDate(access.scheduledAt)} • ${access.durationMinutes || activeMeetingSession?.durationMinutes || 45} min • ${access.interviewerName || 'Interviewer'} & ${access.intervieweeName || 'Interviewee'}`,
    providerLabel: access.providerLabel || providerLabel(access.meetingProvider),
    meetingStatus: access.meetingStatus,
  });
  renderMeetingMeta(access);
  startMeetingTimer(access.meetingStartedAt || access.scheduledAt);
  const external = document.getElementById('meeting-open-external');
  external.hidden = !access.launchUrl;
  if (access.launchUrl) external.href = access.launchUrl;
  if (access.canEmbed && access.embedScriptUrl && access.embedDomain && access.roomName) {
    document.getElementById('meeting-toolbar').hidden = false;
    document.getElementById('meeting-fallback').hidden = true;
    await mountJitsiMeeting(access);
    return;
  }
  document.getElementById('meeting-toolbar').hidden = true;
  destroyMeetingFrame(false);
  renderMeetingFallback(access);
}

function renderMeetingSummary({ title, subtitle, providerLabel: label, meetingStatus }) {
  document.getElementById('meeting-page-heading').textContent = title || 'Interview room';
  document.getElementById('meeting-title').textContent = title || 'Interview room';
  document.getElementById('meeting-subtitle').textContent = subtitle || 'Meeting details will appear here.';
  document.getElementById('meeting-provider-badge').textContent = label || 'Meeting';
  const statusBadge = document.getElementById('meeting-status-badge');
  statusBadge.textContent = meetingStatusText(meetingStatus);
  statusBadge.className = `badge badge-${meetingStatusClass(meetingStatus)}`;
}

function renderMeetingMeta(access) {
  const session = activeMeetingSession || {};
  const scheduledAt = access.scheduledAt || access.startTime || session.startTime;
  const passcodeLine = access.meetingPasscode ? `<dt>Passcode</dt><dd>${esc(access.meetingPasscode)}</dd>` : '';
  const notesLine = session.notes ? `<div class="meeting-meta-card"><h3>Goals</h3><p>${esc(session.notes)}</p></div>` : '';
  document.getElementById('meeting-meta').innerHTML = `
    <div class="meeting-meta-stack">
      <div class="meeting-meta-card">
        <h3>Meeting details</h3>
        <dl>
          <dt>Provider</dt><dd>${esc(access.providerLabel || providerLabel(access.meetingProvider))}</dd>
          <dt>Status</dt><dd>${esc(meetingStatusText(access.meetingStatus))}</dd>
          <dt>Role</dt><dd>${esc(access.participantRole === 'HOST' ? 'Interviewer / host' : 'Interviewee / participant')}</dd>
          <dt>When</dt><dd>${fmtDate(scheduledAt)}</dd>
          <dt>Length</dt><dd>${esc(String(access.durationMinutes || 45))} minutes</dd>
          <dt>Interviewer</dt><dd>${esc(access.interviewerName || 'Interviewer')}</dd>
          <dt>Interviewee</dt><dd>${esc(access.intervieweeName || 'Interviewee')}</dd>
          ${passcodeLine}
        </dl>
      </div>
      ${notesLine}
      <div class="meeting-meta-card">
        <h3>Access</h3>
        <p>${access.canEmbed ? 'This meeting opens directly inside InterviewPrep. You can still launch it in a separate tab if you prefer.' : 'This provider opens securely in a separate browser tab or Zoom web window.'}</p>
        <div class="meeting-launch-row">
          ${access.launchUrl ? `<a class="btn btn-primary btn-sm" href="${esc(access.launchUrl)}" target="_blank" rel="noreferrer">Open link</a>` : ''}
          ${access.joinUrl ? `<a class="btn btn-outline btn-sm" href="${esc(access.joinUrl)}" target="_blank" rel="noreferrer">Join URL</a>` : ''}
        </div>
      </div>
    </div>
  `;
}

function renderMeetingFallback(access) {
  const fallback = document.getElementById('meeting-fallback');
  const embed = document.getElementById('meeting-embed-root');
  embed.innerHTML = '';
  fallback.hidden = false;
  fallback.innerHTML = `
    <div class="meeting-fallback-card">
      <h3>${esc(access.providerLabel || providerLabel(access.meetingProvider))} meeting</h3>
      <p>${access.externalLaunchRequired ? 'This meeting provider launches outside the embedded room. Use the secure link below to continue.' : 'Open the meeting in a new tab if the embedded room is unavailable.'}</p>
      <div class="meeting-launch-row">
        ${access.launchUrl ? `<a class="btn btn-primary" href="${esc(access.launchUrl)}" target="_blank" rel="noreferrer">Open secure meeting</a>` : ''}
        ${access.joinUrl ? `<a class="btn btn-outline" href="${esc(access.joinUrl)}" target="_blank" rel="noreferrer">Copy join path</a>` : ''}
      </div>
    </div>
  `;
}

function renderMeetingError(message) {
  renderMeetingSummary({
    title: sessionTitle(activeMeetingSession) || 'Interview room',
    subtitle: message,
    providerLabel: providerLabel(activeMeetingSession?.meetingProvider),
    meetingStatus: normalizeMeetingStatus(activeMeetingSession || {}),
  });
  document.getElementById('meeting-toolbar').hidden = true;
  document.getElementById('meeting-open-external').hidden = true;
  document.getElementById('meeting-meta').innerHTML = emptyState(message);
  document.getElementById('meeting-embed-root').innerHTML = '';
  const fallback = document.getElementById('meeting-fallback');
  fallback.hidden = false;
  fallback.innerHTML = `<div class="meeting-fallback-card"><h3>Meeting unavailable</h3><p>${esc(message)}</p></div>`;
  resetMeetingTimer();
}

async function mountJitsiMeeting(access) {
  const embed = document.getElementById('meeting-embed-root');
  destroyMeetingFrame(false);
  await loadJitsiScript(access.embedScriptUrl);
  embed.innerHTML = '';
  const options = {
    roomName: access.roomName,
    width: '100%',
    height: '100%',
    parentNode: embed,
    userInfo: {
      displayName: access.displayName || (currentUser.name || currentUser.username || 'InterviewPrep user'),
      email: access.email || currentUser.email || '',
    },
    configOverwrite: {
      prejoinPageEnabled: false,
      disableDeepLinking: true,
      startWithAudioMuted: false,
      startWithVideoMuted: false,
      enableWelcomePage: false,
    },
    interfaceConfigOverwrite: {
      MOBILE_APP_PROMO: false,
    },
  };
  if (access.jwt) options.jwt = access.jwt;
  jitsiApi = new window.JitsiMeetExternalAPI(access.embedDomain, options);
  attachMeetingListener('videoConferenceJoined', () => {
    meetingUiState.joined = true;
    meetingUiState.participants = Math.max(1, meetingUiState.participants);
    if (!activeMeetingAccess?.meetingStartedAt) {
      activeMeetingAccess = { ...activeMeetingAccess, meetingStartedAt: new Date().toISOString(), meetingStatus: 'LIVE' };
      startMeetingTimer(activeMeetingAccess.meetingStartedAt);
    }
    renderMeetingSummary({
      title: activeMeetingAccess?.sessionTitle || access.sessionTitle,
      subtitle: `${fmtDate(access.scheduledAt)} • ${access.durationMinutes || 45} min • ${access.interviewerName || 'Interviewer'} & ${access.intervieweeName || 'Interviewee'}`,
      providerLabel: access.providerLabel || providerLabel(access.meetingProvider),
      meetingStatus: 'LIVE',
    });
    updateMeetingToolbar();
  });
  attachMeetingListener('participantJoined', () => {
    meetingUiState.participants += 1;
    updateMeetingToolbar();
  });
  attachMeetingListener('participantLeft', () => {
    meetingUiState.participants = Math.max(1, meetingUiState.participants - 1);
    updateMeetingToolbar();
  });
  attachMeetingListener('audioMuteStatusChanged', event => {
    meetingUiState.audioMuted = Boolean(event?.muted);
    updateMeetingToolbar();
  });
  attachMeetingListener('videoMuteStatusChanged', event => {
    meetingUiState.videoMuted = Boolean(event?.muted);
    updateMeetingToolbar();
  });
  attachMeetingListener('contentSharingParticipantsChanged', event => {
    const participants = Array.isArray(event?.data) ? event.data : [];
    meetingUiState.screenSharing = participants.length > 0;
    updateMeetingToolbar();
  });
  attachMeetingListener('readyToClose', () => leaveMeetingRoom(true));
  updateMeetingToolbar();
}

function attachMeetingListener(eventName, handler) {
  if (!jitsiApi || typeof handler !== 'function') return;
  if (typeof jitsiApi.addEventListener === 'function') {
    jitsiApi.addEventListener(eventName, handler);
    return;
  }
  if (typeof jitsiApi.addListener === 'function') {
    jitsiApi.addListener(eventName, handler);
  }
}

function loadJitsiScript(src) {
  if (window.JitsiMeetExternalAPI) return Promise.resolve();
  if (jitsiScriptPromise) return jitsiScriptPromise;
  jitsiScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.onload = resolve;
    script.onerror = () => reject(new Error('Could not load meeting embed script.'));
    document.head.appendChild(script);
  });
  return jitsiScriptPromise;
}

function updateMeetingToolbar() {
  const toolbar = document.getElementById('meeting-toolbar');
  if (toolbar.hidden) return;
  const buttons = toolbar.querySelectorAll('.btn');
  buttons[0]?.classList.toggle('active', !meetingUiState.audioMuted);
  if (buttons[0]) buttons[0].textContent = meetingUiState.audioMuted ? 'Mic Off' : 'Mic On';
  buttons[1]?.classList.toggle('active', !meetingUiState.videoMuted);
  if (buttons[1]) buttons[1].textContent = meetingUiState.videoMuted ? 'Camera Off' : 'Camera On';
  buttons[2]?.classList.toggle('active', meetingUiState.screenSharing);
  if (buttons[2]) buttons[2].textContent = meetingUiState.screenSharing ? 'Stop Share' : 'Share Screen';
  if (buttons[3]) buttons[3].textContent = `Tile View (${meetingUiState.participants})`;
}

function toggleMeetingAudio() {
  jitsiApi?.executeCommand?.('toggleAudio');
}

function toggleMeetingVideo() {
  jitsiApi?.executeCommand?.('toggleVideo');
}

function toggleMeetingScreenShare() {
  jitsiApi?.executeCommand?.('toggleShareScreen');
}

function toggleMeetingTileView() {
  jitsiApi?.executeCommand?.('toggleTileView');
}

function leaveMeetingRoom(goBack = false) {
  if (jitsiApi?.executeCommand && !goBack) {
    jitsiApi.executeCommand('hangup');
    return;
  }
  destroyMeetingFrame();
  if (goBack) showSection('sessions');
}

function destroyMeetingFrame(clearAccess = true) {
  clearInterval(activeMeetingTimer);
  activeMeetingTimer = null;
  if (jitsiApi?.dispose) {
    try { jitsiApi.dispose(); } catch {}
  }
  jitsiApi = null;
  document.getElementById('meeting-embed-root').innerHTML = '';
  if (clearAccess) {
    activeMeetingAccess = null;
  }
}

function startMeetingTimer(value) {
  clearInterval(activeMeetingTimer);
  const start = value ? new Date(value).getTime() : NaN;
  if (!Number.isFinite(start)) {
    document.getElementById('meeting-timer').textContent = '00:00';
    return;
  }
  const update = () => {
    const diff = Math.max(0, Date.now() - start);
    const totalSeconds = Math.floor(diff / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    document.getElementById('meeting-timer').textContent = hours > 0
      ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
      : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  };
  update();
  activeMeetingTimer = setInterval(update, 1000);
}

function resetMeetingTimer() {
  clearInterval(activeMeetingTimer);
  activeMeetingTimer = null;
  document.getElementById('meeting-timer').textContent = '00:00';
}

function syncSession(session) {
  const index = sessions.findIndex(item => item.id === session.id);
  if (index === -1) {
    sessions.unshift(session);
    return;
  }
  sessions[index] = { ...sessions[index], ...session };
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
    const payload = {
      name: val('profile-name'),
      bio: val('profile-bio'),
      skills: FormUx.getTagValues('profile-skills'),
      language: FormUx.getLanguageString('profile-language'),
      preferredDomains: FormUx.getTagValues('profile-domains'),
      experienceLevel: val('profile-experience'),
    };
    if (document.getElementById('profile-availability-preference')) {
      payload.availability = profileAvailabilityPayload();
    }
    const updated = await api('/api/users/me/profile', {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
    currentUser = updated;
    localStorage.setItem('ip_user', JSON.stringify(updated));
    let imageUploadError = '';
    if (profileAvatarFile) {
      try {
        currentUser = await uploadSelectedAvatar();
      } catch (err) {
        imageUploadError = err.message || 'Profile image upload failed.';
      }
    }
    initUi();
    renderProfile();
    toast(imageUploadError ? `Profile saved, but image upload failed: ${imageUploadError}` : 'Profile saved.', imageUploadError ? 'error' : 'success');
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
  if (file.size > 5 * 1024 * 1024) {
    resetProfileAvatarSelection();
    toast('Please choose an image under 5 MB.', 'error');
    return;
  }
  if (!PROFILE_IMAGE_TYPES.has(String(file.type || '').toLowerCase())) {
    resetProfileAvatarSelection();
    toast('Only JPG, PNG, WEBP, GIF, and AVIF images are supported.', 'error');
    return;
  }
  releaseProfileAvatarPreview();
  profileAvatarFile = file;
  profileAvatarPreviewUrl = URL.createObjectURL(file);
  renderProfileAvatarPreview();
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
    host.innerHTML = interviewerEmptyState(
      'No practice analytics yet',
      'Completed interview sessions and feedback will unlock real progress insights here.'
    );
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
  if (!source || (typeof source === 'object' && !Array.isArray(source) && Object.keys(source).length === 0)) {
    return [];
  }
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
  syncShellState();
}

function closeModal() {
  document.getElementById('modal-root').innerHTML = '';
  syncShellState();
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
  const name = user?.name || user?.username || user?.email || 'IP';
  return name.split(/\s+/).map(part => part[0]).join('').slice(0, 2).toUpperCase();
}

function interviewerName(interviewer) {
  return interviewer?.name || interviewer?.username || 'Interviewer';
}

function interviewerRole(interviewer) {
  return interviewer?.currentRole || interviewer?.roleTitle || interviewer?.title || 'Interview coach';
}

function interviewerCompany(interviewer) {
  return interviewer?.company || 'Independent';
}

function interviewerMetaLine(interviewer) {
  const role = interviewerRole(interviewer);
  const company = interviewer?.company;
  return company ? `${role} at ${company}` : role;
}

function availabilityLabel(interviewer) {
  return interviewer?.acceptingBookings === false ? 'Not accepting bookings' : 'Available for booking';
}

function bioPreview(value, limit = 150) {
  const text = String(value || 'No bio yet.').trim();
  if (text.length <= limit) return text;
  return `${text.slice(0, limit - 1).trim()}...`;
}

function avatarSource(user, imageUrl = '', size = 96) {
  const raw = imageUrl
    || user?.avatarUrl
    || user?.profileImageUrl
    || user?.profilePhotoUrl
    || user?.photoUrl
    || user?.imageUrl
    || '';
  return optimizeAvatarUrl(raw, size);
}

function optimizeAvatarUrl(url, size = 96) {
  const src = String(url || '').trim();
  if (!src || src.startsWith('blob:') || src.startsWith('data:')) return src;
  if (!src.includes('res.cloudinary.com') || !src.includes('/upload/')) return src;
  if (/\/upload\/[^/]*(c_fill|w_\d+|h_\d+|q_auto|f_auto)[^/]*\//.test(src)) return src;
  const dimension = Math.max(40, Math.min(Number(size) || 96, 240));
  return src.replace('/upload/', `/upload/f_auto,q_auto,c_fill,g_auto,w_${dimension},h_${dimension}/`);
}

function avatarSizeForClass(className) {
  if (String(className).includes('avatar-profile')) return 112;
  if (String(className).includes('large')) return 152;
  if (String(className).includes('avatar-booking')) return 96;
  return 80;
}

function avatarMarkup(user, className = 'avatar', imageUrl = '') {
  const src = avatarSource(user, imageUrl, avatarSizeForClass(className));
  const label = user?.name || user?.username || user?.email || 'InterviewPrep user';
  return `
    <div class="${className}${src ? ' has-image' : ''}">
      <span class="avatar-fallback">${initials(user || { name: label })}</span>
      ${src ? `<img src="${esc(src)}" alt="${esc(label)} avatar" loading="lazy" decoding="async" onerror="this.parentElement.classList.remove('has-image'); this.remove()">` : ''}
    </div>
  `;
}

function currentProfileAvatarUrl() {
  return profileAvatarPreviewUrl || currentUser?.avatarUrl || '';
}

function profileAvatarPreviewCard() {
  const hasPendingUpload = Boolean(profileAvatarFile);
  const hasSavedImage = Boolean(currentUser?.avatarUrl);
  return `
    ${avatarMarkup(currentUser, 'avatar large', currentProfileAvatarUrl())}
    <div class="profile-avatar-copy">
      <strong>Preview your profile image</strong>
      <p>${hasPendingUpload
        ? 'This preview is local for now. Save your profile to upload it securely through the backend and persist it after reload or login.'
        : hasSavedImage
          ? 'Your saved image is already attached to your profile and will keep showing in the sidebar.'
          : 'No profile image yet. Your initials will be used until you upload a photo, logo, avatar, or display picture.'}</p>
      <small>${hasPendingUpload
        ? `${esc(profileAvatarFile.name)} selected`
        : hasSavedImage
          ? 'Cloudinary-backed image saved to your profile.'
          : 'Accepted: JPG, PNG, WEBP, GIF, or AVIF up to 5 MB.'}</small>
    </div>
    ${hasPendingUpload ? '<button class="btn btn-outline btn-sm" type="button" onclick="clearSelectedAvatar()">Clear preview</button>' : ''}
  `;
}

function renderProfileAvatarPreview() {
  const preview = document.getElementById('profile-avatar-preview');
  if (preview) preview.innerHTML = profileAvatarPreviewCard();
  const summaryAvatar = document.getElementById('profile-summary-avatar');
  if (summaryAvatar) summaryAvatar.innerHTML = avatarMarkup(currentUser, 'avatar large', currentProfileAvatarUrl());
}

function clearSelectedAvatar() {
  resetProfileAvatarSelection();
}

function resetProfileAvatarSelection() {
  profileAvatarFile = null;
  releaseProfileAvatarPreview();
  const input = document.getElementById('profile-avatar-file');
  if (input) input.value = '';
  renderProfileAvatarPreview();
}

function releaseProfileAvatarPreview() {
  if (profileAvatarPreviewUrl && profileAvatarPreviewUrl.startsWith('blob:')) {
    URL.revokeObjectURL(profileAvatarPreviewUrl);
  }
  profileAvatarPreviewUrl = '';
}

async function uploadSelectedAvatar() {
  const formData = new FormData();
  formData.append('file', profileAvatarFile);
  const updated = await api('/api/users/me/avatar', {
    method: 'POST',
    body: formData,
  });
  profileAvatarFile = null;
  releaseProfileAvatarPreview();
  localStorage.setItem('ip_user', JSON.stringify(updated));
  return updated;
}

function formatDayLabel(value) {
  const index = AVAILABILITY_DAYS.indexOf(String(value || '').toUpperCase());
  return index === -1 ? String(value || 'Day') : `${AVAILABILITY_DAYS[index].slice(0, 1)}${AVAILABILITY_DAYS[index].slice(1).toLowerCase()}`;
}

function formatPlainTime(value) {
  if (!value) return '--';
  const [hourRaw, minuteRaw] = String(value).split(':');
  const hour = Number(hourRaw);
  const minute = Number(minuteRaw);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return String(value);
  const date = new Date(Date.UTC(2026, 0, 1, hour, minute));
  return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' });
}

function formatWindowSummary(startTime, endTime, durationMinutes) {
  const [startHour, startMinute] = String(startTime || '').split(':').map(Number);
  const [endHour, endMinute] = String(endTime || '').split(':').map(Number);
  if (![startHour, startMinute, endHour, endMinute, Number(durationMinutes)].every(Number.isFinite) || Number(durationMinutes) <= 0) {
    return `${durationMinutes || '--'} min sessions`;
  }
  const totalMinutes = ((endHour * 60) + endMinute) - ((startHour * 60) + startMinute);
  const count = Math.max(0, Math.floor(totalMinutes / Number(durationMinutes)));
  return `${count} ${count === 1 ? 'slot' : 'slots'} x ${durationMinutes} min`;
}

function formatShortDate(value) {
  if (!value) return 'Upcoming';
  try {
    return new Date(value).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
  } catch {
    return String(value);
  }
}

function formatDateTimeRange(startTime, endTime) {
  if (!startTime) return 'Flexible';
  try {
    const start = new Date(startTime);
    const end = endTime ? new Date(endTime) : null;
    const datePart = start.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    const startPart = start.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
    const endPart = end ? end.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }) : '';
    return `${datePart} • ${startPart}${endPart ? ` - ${endPart}` : ''}`;
  } catch {
    return String(startTime);
  }
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

function normalizeMeetingStatus(sessionOrStatus) {
  if (typeof sessionOrStatus === 'string') {
    return String(sessionOrStatus || '').toUpperCase() || 'SCHEDULED';
  }
  const raw = String(sessionOrStatus?.meetingStatus || '').toUpperCase();
  if (raw) return raw;
  const sessionStatus = String(sessionOrStatus?.status || '').toUpperCase();
  if (sessionStatus === 'COMPLETED') return 'COMPLETED';
  if (sessionStatus === 'CANCELLED') return 'CANCELLED';
  return 'SCHEDULED';
}

function meetingStatusText(status) {
  return {
    READY: 'Ready',
    SCHEDULED: 'Scheduled',
    LIVE: 'Live',
    COMPLETED: 'Completed',
    CANCELLED: 'Cancelled',
  }[normalizeMeetingStatus(status)] || 'Scheduled';
}

function meetingStatusClass(status) {
  return {
    READY: 'gray',
    SCHEDULED: 'yellow',
    LIVE: 'green',
    COMPLETED: 'purple',
    CANCELLED: 'red',
  }[normalizeMeetingStatus(status)] || 'gray';
}

function providerLabel(provider) {
  const key = String(provider || '').toUpperCase();
  return meetingProviders.find(item => item.key === key)?.label
    || DEFAULT_MEETING_PROVIDERS.find(item => item.key === key)?.label
    || (key === 'ZOOM' ? 'Zoom' : 'In-platform meeting');
}

function sortSessions(list) {
  return (list || []).slice().sort((a, b) => {
    const leftRaw = new Date(a?.startTime || a?.scheduledAt || 0).getTime();
    const rightRaw = new Date(b?.startTime || b?.scheduledAt || 0).getTime();
    const left = Number.isFinite(leftRaw) ? leftRaw : Number.MAX_SAFE_INTEGER;
    const right = Number.isFinite(rightRaw) ? rightRaw : Number.MAX_SAFE_INTEGER;
    return left - right;
  });
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
