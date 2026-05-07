const API_BASE = window.INTERVIEW_API_BASE;
const USERNAME_PATTERN = /^[a-z0-9._-]{3,24}$/;

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
let profileUsernameTimer = null;
let profileUsernameState = { value: '', available: true };
let discoveryFilterOptions = { expertise: [], languages: [], companies: [], experienceLevels: [], timeZones: [], topics: [], sessionDurations: [] };
let notificationsCache = [];
let notificationStreamController = null;
let notificationStreamRetryTimer = null;
let activeInterviewReport = null;
let sessionViewMode = 'upcoming';
let sessionSearchQuery = '';
let sessionTopicFilter = '';
let prepHub = null;
let prepHubLoading = false;
let prepHubSignature = '';
let prepHubRequestId = 0;
let adminOverview = null;
let adminUsers = [];
let adminSessions = [];
let adminReports = [];
let adminReviews = [];
let adminTrustDashboard = null;
let adminAuditLogPage = null;
let adminLoading = false;
let moderationDialogState = null;
let reportDialogState = null;
let discoverFiltersOpen = false;
let lastDiscoveryResultCount = 0;
let searchSuggestionTimer = null;
const adminState = {
  users: { page: 0, size: 6 },
  sessions: { page: 0, size: 8 },
  reports: { page: 0, size: 5, status: 'OPEN', query: '', category: '' },
  reviews: { page: 0, size: 5, visibility: '', query: '', minRating: '' },
  audit: { page: 0, size: 8, entityType: '', subjectUserId: '' },
};
const REPORT_CATEGORY_OPTIONS = [
  { value: 'SAFETY', label: 'Safety concern', hint: 'Harassment, hate speech, threats, discrimination, or abuse.' },
  { value: 'NO_SHOW', label: 'No-show or lateness', hint: 'Repeated no-shows, very late joins, or frequent reschedules.' },
  { value: 'QUALITY', label: 'Low-quality interview behavior', hint: 'Unprofessional conduct, poor preparation, or misleading guidance.' },
  { value: 'SPAM', label: 'Spam, scam, or solicitation', hint: 'Promotional abuse, off-platform payment pressure, or scams.' },
  { value: 'PROFILE', label: 'Profile misrepresentation', hint: 'False identity, fake credentials, or inaccurate profile claims.' },
  { value: 'OTHER', label: 'Other trust concern', hint: 'Use when no category above fits the issue.' },
];
const DEFAULT_MEETING_PROVIDERS = [
  { key: 'JITSI', label: 'In-platform meeting', embedded: true, enabled: true, isDefault: true },
];
const AVAILABILITY_DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const AVAILABILITY_DURATIONS = [15, 30, 45, 60, 90, 120];
const TOPIC_OPTIONS = ['Java', 'DSA', 'Spring Boot', 'System Design', 'React', 'Node.js', 'SQL', 'Frontend', 'Backend', 'Behavioral', 'Resume Review'];
const DOMAIN_SUGGESTIONS = ['Backend', 'Frontend', 'Full Stack', 'DevOps', 'HR', 'DSA', 'System Design', 'Java', 'React', 'Spring Boot'];
const TIMEZONE_SUGGESTIONS = ['Asia/Kolkata', 'UTC', 'Europe/London', 'America/New_York', 'America/Los_Angeles', 'Asia/Singapore'];
const AVAILABILITY_PREFERENCES = [
  'Weekday mornings',
  'Weekday afternoons',
  'Weekday evenings',
  'Weekend only',
  'Flexible schedule',
  'Late night availability',
  'Early morning availability',
];
const ROUTES = new Set(['overview', 'discover', 'booking', 'sessions', 'meeting', 'feedback', 'career', 'notifications', 'profile', 'interviewer', 'admin-overview', 'admin-users', 'admin-sessions', 'admin-reports']);
const PROFILE_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/avif']);
let bookingState = createBookingState();
let analyticsSummary = null;

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
  restoreDiscoveryFilters();
  initUi();
  bindFilters();
  refreshDiscoverFilterUi();
  bindFeedbackForm();
  await Promise.all([
    loadFilterOptions(),
    loadMeetingProviders(),
    loadSessions(),
    loadInterviewers(),
    loadRecommended(),
    loadFeedback(),
    loadNotifications(),
    loadAvailabilityManagement(),
    loadPrepHub(),
    loadAdminData(),
  ]);
  startNotificationStream();
  showSection(routeFromHash(), false);
});

window.addEventListener('hashchange', () => showSection(routeFromHash(), false));
window.addEventListener('resize', handleResponsiveShell);
document.addEventListener('keydown', handleGlobalKeydown);

function readJson(key) {
  try { return JSON.parse(localStorage.getItem(key)); } catch { return null; }
}

function logout() {
  stopNotificationStream();
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
  document.getElementById('welcome-heading').textContent = `Welcome back, ${accountDisplayName(currentUser)}`;
  document.getElementById('role-eyebrow').textContent = activeWorkspace === 'ADMIN'
    ? 'Admin control center'
    : workspace === 'interviewer'
      ? 'Interviewer workspace'
      : 'Interviewee workspace';
  document.getElementById('sidebar-user-info').innerHTML = `
    <div class="sidebar-profile-card">
      ${avatarMarkup(currentUser)}
      <div class="sidebar-profile-copy">
        <strong>${esc(accountDisplayName(currentUser))}</strong>
        <span>${esc(currentUser.email || '')}</span>
        <span class="badge ${activeWorkspace === 'ADMIN' ? 'badge-gold' : 'badge-purple'}">${esc(workspace)}</span>
      </div>
    </div>
  `;
  renderWorkspaceSwitcher(roles);
  applyWorkspaceVisibility();
  document.getElementById('primary-action').textContent = activeWorkspace === 'ADMIN'
    ? 'Open console'
    : workspace === 'interviewer'
      ? 'View requests'
      : 'Book session';
  renderBookingStep();
  renderResumePanel();
  renderPrepPanels();
  renderAdminPanels();
}

function userRoles() {
  const roles = Array.isArray(currentUser.roles) && currentUser.roles.length ? currentUser.roles : [currentUser.role || 'INTERVIEWEE'];
  return [...new Set(roles.map(role => String(role || '').toUpperCase()).filter(role => ['INTERVIEWEE', 'INTERVIEWER', 'ADMIN'].includes(role)))];
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

function hasIntervieweeRole() {
  return userRoles().includes('INTERVIEWEE');
}

function hasAdminRole() {
  return userRoles().includes('ADMIN');
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
  loadAnalyticsSummary();
  loadAdminData();
  loadPrepHub();
}

function primaryWorkspaceAction() {
  if (activeWorkspace === 'ADMIN') {
    showSection('admin-overview');
    return;
  }
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
  if (role === 'INTERVIEWER') return 'Interviewer Workspace';
  if (role === 'ADMIN') return 'Admin Workspace';
  return 'Interviewee Workspace';
}

function applyWorkspaceVisibility() {
  const isAdmin = activeWorkspace === 'ADMIN';
  const isInterviewer = activeWorkspace === 'INTERVIEWER';
  document.querySelectorAll('.admin-only').forEach(el => el.style.display = isAdmin ? '' : 'none');
  document.querySelectorAll('.user-workspace').forEach(el => el.style.display = isAdmin ? 'none' : '');
  document.querySelectorAll('.user-workspace-section').forEach(el => { if (isAdmin) el.hidden = true; });
  document.querySelectorAll('.interviewer-only').forEach(el => el.style.display = !isAdmin && isInterviewer ? 'flex' : 'none');
  document.querySelectorAll('.interviewee-workspace').forEach(el => el.style.display = !isAdmin && isInterviewer ? 'none' : '');
  document.body.classList.toggle('interviewer-workspace-active', isInterviewer);
  document.body.classList.toggle('admin-workspace-active', isAdmin);
  document.querySelector('.bottom-nav')?.classList.toggle('hidden', isAdmin);
}

function routeAllowed(route) {
  if (activeWorkspace === 'ADMIN') return ['admin-overview', 'admin-users', 'admin-sessions', 'admin-reports', 'profile'].includes(route);
  if (activeWorkspace === 'INTERVIEWER') return !['discover', 'booking'].includes(route);
  if (route === 'interviewer') return false;
  if (route.startsWith('admin-')) return false;
  return true;
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
  let targetName = ROUTES.has(name) ? name : defaultWorkspaceRoute();
  if (!routeAllowed(targetName)) targetName = defaultWorkspaceRoute();
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
  if (targetName === 'discover') refreshDiscoverFilterUi();
  if (targetName !== 'discover') {
    discoverFiltersOpen = false;
    hideSearchSuggestions();
    refreshDiscoverFilterUi();
  }
  if (targetName === 'meeting' && !activeMeetingSession) renderMeetingPlaceholder();
  if (targetName === 'feedback') populateFeedbackSessions();
  if (targetName === 'sessions') renderSessions('upcoming');
  if (targetName === 'notifications') loadNotifications();
  if (targetName === 'profile') renderProfile();
  if (targetName === 'career') {
    renderResumePanel();
    renderPrepPanels();
  }
  if (targetName.startsWith('admin-')) {
    loadAdminData();
    renderAdminPanels();
  }
  document.querySelector('.topbar')?.classList.toggle('compact', targetName !== 'overview');
  if (window.innerWidth < 900) document.getElementById('sidebar').classList.remove('open');
  syncShellState();
  if (updateRoute && window.location.hash !== `#/${targetName}`) {
    history.pushState(null, '', `#/${targetName}`);
  }
}

function defaultWorkspaceRoute() {
  if (activeWorkspace === 'ADMIN') return 'admin-overview';
  return 'overview';
}

function handleResponsiveShell() {
  if (window.innerWidth >= 761) {
    document.getElementById('sidebar')?.classList.remove('open');
    discoverFiltersOpen = false;
  }
  refreshDiscoverFilterUi();
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
  initDiscoveryFilterControls();
  ['search-q', 'filter-expertise', 'filter-company', 'filter-language', 'filter-timezone', 'filter-topic', 'filter-years', 'filter-level', 'filter-rating', 'filter-duration', 'filter-available', 'filter-available-today', 'filter-free', 'filter-verified', 'filter-sort']
    .forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      const eventName = el.tagName === 'SELECT' || el.type === 'checkbox' ? 'change' : 'input';
      el.addEventListener(eventName, () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
          interviewerPage = 0;
          persistDiscoveryFilters();
          renderActiveFilterChips();
          loadInterviewers();
        }, 280);
      });
    });
  document.getElementById('search-q')?.addEventListener('input', () => {
    clearTimeout(searchSuggestionTimer);
    searchSuggestionTimer = setTimeout(loadSearchSuggestions, 180);
  });
  document.addEventListener('pointerdown', event => {
    const suggestionHost = document.getElementById('search-suggestions');
    const searchWrap = document.querySelector('.discover-search-group');
    if (!suggestionHost || suggestionHost.hidden) return;
    if (searchWrap && !searchWrap.contains(event.target)) hideSearchSuggestions();
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') hideSearchSuggestions();
  });
  renderActiveFilterChips();
  refreshDiscoverFilterUi();
}

function initDiscoveryFilterControls() {
  FormUx.initSearchSelect('filter-expertise', {
    placeholder: 'Expertise',
    label: 'Filter by interviewer expertise',
    className: 'discovery-filter-control',
    options: discoveryFilterOptions.expertise,
  });
  FormUx.initSearchSelect('filter-company', {
    placeholder: 'Company',
    label: 'Filter by interviewer company',
    className: 'discovery-filter-control',
    options: discoveryFilterOptions.companies,
  });
  FormUx.initSearchSelect('filter-language', {
    placeholder: 'Language',
    label: 'Filter by interviewer language',
    className: 'discovery-filter-control',
    options: discoveryFilterOptions.languages,
  });
  FormUx.initSearchSelect('filter-timezone', {
    placeholder: 'Timezone',
    label: 'Filter by timezone',
    className: 'discovery-filter-control',
    options: discoveryFilterOptions.timeZones.length ? discoveryFilterOptions.timeZones : TIMEZONE_SUGGESTIONS,
    preserveCase: true,
  });
  FormUx.initSearchSelect('filter-topic', {
    placeholder: 'Topic',
    label: 'Filter by interview topic',
    className: 'discovery-filter-control',
    options: discoveryFilterOptions.topics.length ? discoveryFilterOptions.topics : TOPIC_OPTIONS,
  });
}

async function loadFilterOptions() {
  try {
    const data = await api('/api/interviewers/filter-options');
    discoveryFilterOptions = {
      expertise: Array.isArray(data?.expertise) ? data.expertise : [],
      languages: Array.isArray(data?.languages) ? data.languages : [],
      companies: Array.isArray(data?.companies) ? data.companies : [],
      experienceLevels: Array.isArray(data?.experienceLevels) ? data.experienceLevels : [],
      timeZones: Array.isArray(data?.timeZones) ? data.timeZones : [],
      topics: Array.isArray(data?.topics) ? data.topics : [],
      sessionDurations: Array.isArray(data?.sessionDurations) ? data.sessionDurations : [],
    };
    updateDiscoveryFilterOptions();
  } catch (err) {
    discoveryFilterOptions = { expertise: [], languages: [], companies: [], experienceLevels: [], timeZones: [], topics: [], sessionDurations: [] };
  }
}

function updateDiscoveryFilterOptions() {
  document.getElementById('filter-expertise')?.__searchSelectControl?.setOptions(discoveryFilterOptions.expertise);
  document.getElementById('filter-company')?.__searchSelectControl?.setOptions(discoveryFilterOptions.companies);
  document.getElementById('filter-language')?.__searchSelectControl?.setOptions(discoveryFilterOptions.languages);
  document.getElementById('filter-timezone')?.__searchSelectControl?.setOptions(discoveryFilterOptions.timeZones.length ? discoveryFilterOptions.timeZones : TIMEZONE_SUGGESTIONS);
  document.getElementById('filter-topic')?.__searchSelectControl?.setOptions(discoveryFilterOptions.topics.length ? discoveryFilterOptions.topics : TOPIC_OPTIONS);
  const levelSelect = document.getElementById('filter-level');
  if (levelSelect) {
    const current = levelSelect.value;
    const levels = Array.isArray(discoveryFilterOptions.experienceLevels) ? discoveryFilterOptions.experienceLevels : [];
    levelSelect.innerHTML = `<option value="">Any level</option>${levels.map(level => `<option value="${esc(level)}">${esc(level)}</option>`).join('')}`;
    if (current) levelSelect.value = current;
  }
  const durationSelect = document.getElementById('filter-duration');
  if (durationSelect && Array.isArray(discoveryFilterOptions.sessionDurations) && discoveryFilterOptions.sessionDurations.length) {
    const current = durationSelect.value;
    durationSelect.innerHTML = `<option value="">Any duration</option>${discoveryFilterOptions.sessionDurations.map(value => `<option value="${value}">${value} min</option>`).join('')}`;
    if (current) durationSelect.value = current;
  }
  renderActiveFilterChips();
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
  updateDiscoverFilterSummary();
  persistDiscoveryFilters();
  renderActiveFilterChips();
  if (activeWorkspace === 'INTERVIEWER') {
    grid.innerHTML = emptyState('Switch to the interviewee workspace to browse interviewers.');
    return;
  }
  if (activeWorkspace === 'ADMIN') {
    grid.innerHTML = emptyState('Admin workspace uses the control center instead of interviewer search.');
    return;
  }
  grid.innerHTML = skeletonCards(6);
  lastDiscoveryResultCount = 0;
  updateDiscoverFilterSummary();
  try {
    const params = new URLSearchParams({
      q: val('search-q'),
      expertise: val('filter-expertise'),
      company: val('filter-company'),
      language: val('filter-language'),
      timezone: val('filter-timezone'),
      topic: val('filter-topic'),
      sort: val('filter-sort'),
      excludeUserId: currentUser.id,
      page: interviewerPage,
      size: 9,
    });
    if (val('filter-years')) params.set('minExperience', val('filter-years'));
    if (val('filter-level')) params.set('experienceLevel', val('filter-level'));
    if (val('filter-rating')) params.set('minRating', val('filter-rating'));
    if (val('filter-duration')) params.set('sessionDuration', val('filter-duration'));
    if (document.getElementById('filter-available').checked) params.set('available', 'true');
    if (document.getElementById('filter-available-today').checked) params.set('availableToday', 'true');
    if (document.getElementById('filter-free').checked) params.set('free', 'true');
    if (document.getElementById('filter-verified').checked) params.set('verified', 'true');
    params.set('viewerTimezone', viewerTimezone());
    const page = await api(`/api/interviewers/search?${params.toString()}`);
    interviewers = filterSelf(page.items || []);
    rememberInterviewers(interviewers);
    interviewerTotalPages = Math.max(1, page.totalPages || 1);
    lastDiscoveryResultCount = Number(page.totalItems || interviewers.length || 0);
    renderInterviewerGrid(interviewers);
    refreshSessionSurfaces();
    document.getElementById('page-label').textContent = `Page ${interviewerPage + 1} of ${interviewerTotalPages}`;
    updateDiscoverFilterSummary();
  } catch (err) {
    grid.innerHTML = emptyState('Could not load interviewers.');
    lastDiscoveryResultCount = 0;
    updateDiscoverFilterSummary();
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
  list.innerHTML = `
    <div class="recommendation-loading-note">
      <strong>Loading recommendations</strong>
      <p>Ranking verified, available interviewers for your next session.</p>
    </div>
    ${skeletonCards(3)}
  `;
  try {
    const data = await api(`/api/interviewers/recommended?intervieweeId=${currentUser.id}`);
    const recommendations = filterSelf(data || [])
      .sort((a, b) => recommendationScore(b) - recommendationScore(a))
      .slice(0, 4);
    rememberInterviewers(recommendations);
    list.innerHTML = recommendations.map((item, index) => renderCompactInterviewer(item, index)).join('') || interviewerEmptyState(
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
    lastDiscoveryResultCount = 0;
    updateDiscoverFilterSummary();
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
  const topics = Array.isArray(interviewer.interviewTopics) ? interviewer.interviewTopics.slice(0, 3) : [];
  const durations = Array.isArray(interviewer.sessionDurations) ? interviewer.sessionDurations.slice(0, 3) : [];
  const reliability = Number(interviewer.reliabilityScore || 0);
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
            ${Number.isFinite(reliability) ? `<span>${esc(String(reliability.toFixed(1)))}% reliability</span>` : ''}
            ${interviewer.interviewerVerified ? '<span class="badge badge-green">Verified</span>' : ''}
          </div>
          <div class="tag-row">${skills.map(skill => `<span>${esc(skill)}</span>`).join('') || '<span>Interview coaching</span>'}</div>
          ${topics.length ? `<div class="tag-row subtle">${topics.map(topic => `<span>${esc(topic)}</span>`).join('')}</div>` : ''}
        </div>
      </div>
      <p class="bio interviewer-bio">${esc(bioPreview(interviewer.bio))}</p>
      <div class="interviewer-card-footer">
        <span class="availability-pill ${interviewer.acceptingBookings === false ? 'is-muted' : ''}">${esc(availabilityLabel(interviewer))}</span>
        <span class="availability-pill">${esc(interviewer.timeZone || 'Timezone flexible')}</span>
        ${durations.length ? `<span class="availability-pill">${esc(durations.join(' / '))} min</span>` : ''}
        <div class="card-actions">
          <button class="btn btn-outline btn-sm" onclick="openProfile('${interviewer.id}')">Profile</button>
          <button class="btn btn-primary btn-sm" onclick="selectInterviewer('${interviewer.id}')">Book</button>
        </div>
      </div>
    </article>
  `;
}

function renderCompactInterviewer(interviewer, index = 0) {
  const score = recommendationScore(interviewer);
  const rankLabel = recommendationRankLabel(score, index);
  const availability = interviewer?.acceptingBookings === false ? 'Unavailable now' : 'Available';
  return `
    <button class="mini-interviewer recommendation-card" onclick="selectInterviewer('${interviewer.id}')">
      ${avatarMarkup(interviewer, 'avatar avatar-compact')}
      <span class="mini-interviewer-copy">
        <strong>${esc(interviewerName(interviewer))}</strong>
        <small>${esc(interviewerRole(interviewer))}</small>
        <small class="recommendation-meta">
          <span class="recommendation-rank">${esc(rankLabel)}</span>
          <span class="recommendation-availability ${interviewer?.acceptingBookings === false ? 'is-muted' : ''}">${esc(availability)}</span>
          ${interviewer?.interviewerVerified ? '<span class="recommendation-verified">Verified</span>' : ''}
        </small>
      </span>
    </button>
  `;
}

function recommendationScore(interviewer) {
  const verifiedBoost = interviewer?.interviewerVerified ? 200 : 0;
  const availabilityBoost = interviewer?.acceptingBookings === false ? 0 : 120;
  const ratingBoost = Math.round(Number(interviewer?.averageRating || 0) * 20);
  const reviewBoost = Math.min(80, Number(interviewer?.reviewCount || 0));
  const sessionsBoost = Math.min(100, Number(interviewer?.completedInterviews || 0));
  return verifiedBoost + availabilityBoost + ratingBoost + reviewBoost + sessionsBoost;
}

function recommendationRankLabel(score, index) {
  if (index === 0) return 'Top match';
  if (score >= 360) return 'Strong match';
  if (score >= 260) return 'Good match';
  return `Match #${index + 1}`;
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
    const publicUrl = publicProfileUrl(interviewer.username || interviewer.id);
    const reliability = Number(interviewer.reliabilityScore || 0);
    modal(`
      <div class="profile-modal">
        ${avatarMarkup(interviewer, 'avatar large')}
        <h2>${esc(accountDisplayName(interviewer))}</h2>
        <p>${esc(interviewer.currentRole || 'Interview coach')} ${interviewer.company ? `at ${esc(interviewer.company)}` : ''}</p>
        <div class="tag-row">${(interviewer.skills || []).map(skill => `<span>${esc(skill)}</span>`).join('')}</div>
        <p>${esc(interviewer.bio || 'No bio yet.')}</p>
        <div class="stats-inline"><span>${interviewer.yearsExperience || 0}+ years</span><span>${ratingSummary(interviewer)}</span><span>${interviewer.completedInterviews || 0} completed</span></div>
        <div class="stats-inline"><span>${esc(interviewer.timeZone || 'Timezone flexible')}</span><span>${esc(String(reliability.toFixed(1)))}% reliability</span>${interviewer.interviewerVerified ? '<span>Verified interviewer</span>' : ''}</div>
        <div class="profile-modal-actions">
          <a class="btn btn-outline btn-full" href="${esc(publicUrl)}" target="_blank" rel="noreferrer">Open public profile</a>
          <button class="btn btn-outline btn-full" onclick="copyText(${jsArg(publicUrl)}, 'Public profile link copied.')">Copy profile link</button>
          <button class="btn btn-outline btn-full" onclick="reportUser('${interviewer.id}')">Report profile</button>
          <button class="btn btn-primary btn-full" onclick="closeModal(); selectInterviewer('${interviewer.id}')">Book this interviewer</button>
        </div>
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
          <p class="availability-muted">${esc(accountDisplayName(interviewer))} has real generated slots for the next two weeks.</p>
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
          <p class="availability-muted">${esc(accountDisplayName(interviewer))} - ${esc(selectedDay?.dateLabel || 'Pick a date')}</p>
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
      item.displayName,
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
  const label = accountDisplayName(interviewer) || 'Selected interviewer';
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
    await loadAnalyticsSummary();
    if (activeMeetingSession?.id) {
      activeMeetingSession = sessions.find(item => item.id === activeMeetingSession.id) || activeMeetingSession;
      if (!document.getElementById('section-meeting').hidden) renderMeetingMeta(activeMeetingAccess || activeMeetingSession);
    }
    renderOverview();
    renderSessions('upcoming');
    renderInterviewerPanel();
    populateFeedbackSessions();
    populateSessionFilters();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function loadAnalyticsSummary() {
  try {
    analyticsSummary = await api(`/api/analytics/summary?workspace=${encodeURIComponent(activeWorkspace)}`);
  } catch {
    analyticsSummary = null;
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
  const upcomingCount = analyticsSummary?.upcoming ?? upcoming.length;
  const completedCount = analyticsSummary?.completed ?? completed.length;
  const rating = analyticsSummary?.averageRating;
  document.getElementById('stat-upcoming').textContent = String(upcomingCount);
  document.getElementById('stat-completed').textContent = String(completedCount);
  document.getElementById('stat-rating').textContent = Number.isFinite(Number(rating)) && Number(rating) > 0 ? Number(rating).toFixed(1) : (ratingValue(currentUser) || '-');
  const streakCard = document.getElementById('stat-streak-card');
  const streakValue = analyticsSummary?.streakDays;
  const hasPracticeStreak = Number.isFinite(Number(streakValue)) && Number(streakValue) > 0;
  if (streakCard) {
    streakCard.hidden = !hasPracticeStreak;
    if (hasPracticeStreak) document.getElementById('stat-streak').textContent = String(Number(streakValue));
  }
  document.getElementById('upcoming-list').innerHTML = upcoming.slice(0, 4).map(renderSessionCard).join('') || emptyState('No upcoming sessions.');
  renderSkillProgress();
}

function renderSessions(mode = 'upcoming') {
  sessionViewMode = mode;
  document.querySelectorAll('.session-tabs .chip').forEach((chip, index) => chip.classList.toggle('active', (mode === 'upcoming') === (index === 0)));
  const base = mode === 'upcoming'
    ? sessions.filter(item => ['PENDING', 'CONFIRMED'].includes((item.status || '').toUpperCase()))
    : sessions.filter(item => ['COMPLETED', 'CANCELLED'].includes((item.status || '').toUpperCase()));
  const filtered = base.filter(item => sessionMatchesFilters(item));
  const list = sortSessions(filtered);
  document.getElementById('sessions-list').innerHTML = list.map(renderSessionCard).join('') || emptyState('No sessions here yet.');
}

function setSessionSearch(value) {
  sessionSearchQuery = String(value || '').trim().toLowerCase();
  renderSessions(sessionViewMode);
}

function setSessionTopic(value) {
  sessionTopicFilter = String(value || '').trim();
  renderSessions(sessionViewMode);
}

function populateSessionFilters() {
  const topicSelect = document.getElementById('session-topic-filter');
  if (!topicSelect) return;
  const topics = Array.from(new Set(sessions.flatMap(session => sessionTopics(session)).map(item => String(item))))
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b));
  const current = sessionTopicFilter;
  topicSelect.innerHTML = `<option value="">All topics</option>${topics.map(topic => `<option value="${esc(topic)}">${esc(topic)}</option>`).join('')}`;
  if (current) topicSelect.value = current;
}

function sessionMatchesFilters(session) {
  if (!session) return false;
  if (sessionTopicFilter) {
    const topics = sessionTopics(session);
    if (!topics.some(topic => topic === sessionTopicFilter)) return false;
  }
  if (!sessionSearchQuery) return true;
  const title = sessionTitle(session).toLowerCase();
  const topicsText = sessionTopics(session).join(' ').toLowerCase();
  const status = String(session.status || '').toLowerCase();
  const participant = sessionParticipant(session);
  const participantText = accountDisplayName(participant).toLowerCase();
  return [title, topicsText, status, participantText].some(value => value.includes(sessionSearchQuery));
}

function renderSessionCard(session) {
  const status = (session.status || 'PENDING').toUpperCase();
  const meetingStatus = normalizeMeetingStatus(session);
  const isInterviewer = activeWorkspace === 'INTERVIEWER';
  const canConfirm = isInterviewer && status === 'PENDING';
  const canComplete = status === 'CONFIRMED';
  const canOpenMeeting = ['CONFIRMED'].includes(status) && !['COMPLETED', 'CANCELLED'].includes(meetingStatus);
  const joinLabel = currentUser.id === session.interviewerId && meetingStatus !== 'LIVE' ? 'Start Interview' : 'Join Interview';
  const hasMeeting = Boolean(session.meetingId || session.joinUrl || session.meetingLink || session.meetingProvider);
  const meetingNote = status === 'PENDING'
    ? 'Meeting room unlocks after interviewer approval.'
    : !hasMeeting && ['CONFIRMED'].includes(status)
      ? 'Meeting room is being prepared.'
      : '';
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
      ${meetingNote ? `<p class="session-meeting-note">${esc(meetingNote)}</p>` : ''}
      <div class="session-actions">
        ${hasMeeting && canOpenMeeting ? `<button class="btn btn-primary btn-sm" onclick="openMeeting('${session.id}')">${joinLabel}</button>` : ''}
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
    populateFeedbackSessions();
    renderOverview();
    renderSessions('upcoming');
    renderInterviewerPanel();
  } catch {
    feedbackItems = [];
    document.getElementById('feedback-list').innerHTML = emptyState('Feedback will appear here.');
    document.getElementById('recent-feedback').innerHTML = emptyState('Feedback will appear here.');
    populateFeedbackSessions();
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
  const eligible = sessions.filter(item =>
    (item.status || '').toUpperCase() === 'COMPLETED'
    && !hasCurrentUserReviewedSession(item.id)
  );
  select.innerHTML = eligible.map(item => `<option value="${esc(item.id)}">${esc(sessionTitle(item))} - ${fmtDate(item.startTime)}</option>`).join('') || '<option value="">No eligible sessions</option>';
  renderFeedbackTopicSections();
}

function hasCurrentUserReviewedSession(sessionId) {
  if (!sessionId || !currentUser?.id) return false;
  return feedbackItems.some(item => item?.sessionId === sessionId && item?.reviewerId === currentUser.id);
}

function renderFeedback(item) {
  const topics = item.topicFeedback || [];
  const session = item.sessionId ? sessions.find(entry => entry.id === item.sessionId) : null;
  const looksLikeReport = session && item.reviewerId && session.interviewerId && item.reviewerId === session.interviewerId;
  const reportBtn = looksLikeReport
    ? `<button class="btn btn-outline btn-sm" onclick="openInterviewReport(${jsArg(item.sessionId)})">View report</button>`
    : '';
  return `
    <article class="feedback-item">
      <div class="feedback-item-head"><strong>${esc(String(item.rating || '-'))}/5</strong>${topics.length ? `<div class="topic-chip-row">${topicTags(topics.map(topic => topic.topic))}</div>` : ''}</div>
      <span>${esc(item.comments || '')}</span>
      ${topics.length ? `<div class="feedback-topic-summary">${topics.map(topic => `<span>${esc(topic.topic)}${topic.rating ? `: ${esc(String(topic.rating))}/5` : ''}</span>`).join('')}</div>` : ''}
      ${item.improvementAreas || item.recommendations ? `<small>${esc(item.improvementAreas || item.recommendations)}</small>` : ''}
      ${reportBtn ? `<div class="feedback-item-actions">${reportBtn}</div>` : ''}
    </article>
  `;
}

async function openInterviewReport(sessionId) {
  modal(`
    <div class="modal-head">
      <h2>Interview report</h2>
      <p class="muted">Loading report...</p>
    </div>
  `);
  try {
    const report = await api(`/api/reports/session/${encodeURIComponent(sessionId)}`);
    activeInterviewReport = report;
    modal(renderInterviewReport(report));
  } catch (err) {
    activeInterviewReport = null;
    modal(`
      <div class="modal-head">
        <h2>Interview report</h2>
        <p class="muted">${esc(err.message || 'Report unavailable')}</p>
      </div>
      <div class="modal-actions">
        <button class="btn btn-outline" onclick="closeModal()">Close</button>
      </div>
    `);
  }
}

function renderInterviewReport(report) {
  const topics = Array.isArray(report?.topicReports) ? report.topicReports : [];
  const headerTitle = report?.topics?.length ? report.topics.join(', ') : 'Interview report';
  const when = report?.sessionStartTime ? fmtDate(report.sessionStartTime) : '';
  const participants = `${report?.interviewerName || 'Interviewer'} • ${report?.intervieweeName || 'Interviewee'}`;
  return `
    <div class="modal-head">
      <h2>${esc(headerTitle)}</h2>
      <p class="muted">${esc(when)}${when ? ' • ' : ''}${esc(participants)}</p>
    </div>
    <div class="report-actions-row">
      <button class="btn btn-outline btn-sm" onclick="printActiveInterviewReport()">Print / Save PDF</button>
      <button class="btn btn-primary btn-sm" onclick="closeModal()">Done</button>
    </div>
    <div class="report-body">
      <div class="report-summary-grid">
        <div class="report-metric"><span>Overall rating</span><strong>${esc(String(report?.overallRating || '-'))}/5</strong></div>
        <div class="report-metric"><span>Topics</span><strong>${esc((report?.topics || []).join(', ') || '—')}</strong></div>
      </div>
      ${report?.strengths ? `<details class="report-block" open><summary>Strengths</summary><p>${esc(report.strengths)}</p></details>` : ''}
      ${report?.weaknesses ? `<details class="report-block" open><summary>Areas to improve</summary><p>${esc(report.weaknesses)}</p></details>` : ''}
      ${report?.improvementRoadmap ? `<details class="report-block" open><summary>Improvement roadmap</summary><p>${esc(report.improvementRoadmap)}</p></details>` : ''}
      ${report?.interviewerComments ? `<details class="report-block" open><summary>Interviewer comments</summary><p>${esc(report.interviewerComments)}</p></details>` : ''}
      ${topics.length ? `
        <div class="divider"></div>
        <h3 class="report-subhead">Topic-wise feedback</h3>
        <div class="report-topic-grid">
          ${topics.map(topic => renderTopicReport(topic)).join('')}
        </div>
      ` : ''}
    </div>
  `;
}

function printActiveInterviewReport() {
  if (!activeInterviewReport) return;
  printInterviewReport(activeInterviewReport);
}

function renderTopicReport(topic) {
  const rating = topic?.rating ? `${Number(topic.rating)}/5` : '—';
  const skills = topic?.skillRatings && typeof topic.skillRatings === 'object'
    ? Object.entries(topic.skillRatings).filter(([, value]) => value).slice(0, 6)
    : [];
  return `
    <details class="report-topic-card" open>
      <summary>
        <span>${esc(topic?.topic || 'Topic')}</span>
        <small>${esc(rating)}</small>
      </summary>
      ${skills.length ? `<div class="report-skill-row">${skills.map(([name, value]) => `<span>${esc(name)}: ${esc(String(value))}/5</span>`).join('')}</div>` : ''}
      ${topic?.strengths ? `<p><strong>Strengths:</strong> ${esc(topic.strengths)}</p>` : ''}
      ${topic?.weaknesses ? `<p><strong>Weaknesses:</strong> ${esc(topic.weaknesses)}</p>` : ''}
      ${topic?.improvementAreas ? `<p><strong>Next steps:</strong> ${esc(topic.improvementAreas)}</p>` : ''}
      ${topic?.comments ? `<p><strong>Comments:</strong> ${esc(topic.comments)}</p>` : ''}
    </details>
  `;
}

function printInterviewReport(report) {
  const payload = typeof report === 'string' ? (() => { try { return JSON.parse(report); } catch { return null; } })() : report;
  if (!payload) return;
  const title = payload?.topics?.length ? payload.topics.join(', ') : 'Interview report';
  const when = payload?.sessionStartTime ? fmtDate(payload.sessionStartTime) : '';
  const participants = `${payload?.interviewerName || 'Interviewer'} • ${payload?.intervieweeName || 'Interviewee'}`;
  const html = `
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>${esc(title)}</title>
      <style>
        body { font-family: Inter, system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; margin: 32px; color: #0f172a; }
        h1 { margin: 0 0 6px; font-size: 22px; }
        .muted { color: #475569; font-size: 13px; margin: 0 0 18px; }
        .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 14px 0 18px; }
        .card { border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; }
        .card span { display:block; font-size: 12px; color:#64748b; margin-bottom: 6px; }
        .card strong { font-size: 16px; }
        h2 { font-size: 14px; margin: 22px 0 8px; }
        .block { border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
        .topic { page-break-inside: avoid; }
        .topic-head { display:flex; justify-content:space-between; gap: 12px; }
        .topic small { color:#64748b; }
        p { margin: 8px 0 0; font-size: 13px; line-height: 1.4; }
      </style>
    </head>
    <body>
      <h1>${esc(title)}</h1>
      <p class="muted">${esc(when)}${when ? ' • ' : ''}${esc(participants)}</p>
      <div class="grid">
        <div class="card"><span>Overall rating</span><strong>${esc(String(payload?.overallRating || '-'))}/5</strong></div>
        <div class="card"><span>Topics</span><strong>${esc((payload?.topics || []).join(', ') || '—')}</strong></div>
      </div>
      ${payload?.strengths ? `<div class="block"><h2>Strengths</h2><p>${esc(payload.strengths)}</p></div>` : ''}
      ${payload?.weaknesses ? `<div class="block"><h2>Areas to improve</h2><p>${esc(payload.weaknesses)}</p></div>` : ''}
      ${payload?.improvementRoadmap ? `<div class="block"><h2>Improvement roadmap</h2><p>${esc(payload.improvementRoadmap)}</p></div>` : ''}
      ${payload?.interviewerComments ? `<div class="block"><h2>Interviewer comments</h2><p>${esc(payload.interviewerComments)}</p></div>` : ''}
      ${(payload?.topicReports || []).length ? `
        <h2>Topic-wise feedback</h2>
        ${(payload.topicReports || []).map(topic => `
          <div class="block topic">
            <div class="topic-head">
              <strong>${esc(topic?.topic || 'Topic')}</strong>
              <small>${esc(topic?.rating ? `${topic.rating}/5` : '—')}</small>
            </div>
            ${topic?.strengths ? `<p><strong>Strengths:</strong> ${esc(topic.strengths)}</p>` : ''}
            ${topic?.weaknesses ? `<p><strong>Weaknesses:</strong> ${esc(topic.weaknesses)}</p>` : ''}
            ${topic?.improvementAreas ? `<p><strong>Next steps:</strong> ${esc(topic.improvementAreas)}</p>` : ''}
            ${topic?.comments ? `<p><strong>Comments:</strong> ${esc(topic.comments)}</p>` : ''}
          </div>
        `).join('')}
      ` : ''}
      <script>window.addEventListener('load', () => setTimeout(() => window.print(), 50));</script>
    </body>
    </html>
  `;
  const w = window.open('', '_blank', 'noopener,noreferrer');
  if (!w) return;
  w.document.open();
  w.document.write(html);
  w.document.close();
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
    const data = await api('/api/notifications?limit=30');
    notificationsCache = Array.isArray(data.items) ? data.items : [];
    setUnreadBadge(Number(data.unread || 0));
    renderNotifications(notificationsCache);
  } catch {
    notificationsCache = [];
    setUnreadBadge(0);
    renderNotifications([]);
  }
}

function renderNotifications(items) {
  const panel = document.getElementById('notification-panel');
  const grouped = groupNotifications(items || []);
  const head = `
    <div class="notification-head">
      <strong>Notifications</strong>
      <div class="notification-actions">
        <button class="btn btn-ghost btn-sm" onclick="markAllNotificationsRead()">Mark all read</button>
        <button class="icon-btn icon-btn-sm" onclick="toggleNotifications(false)" aria-label="Close notifications">✕</button>
      </div>
    </div>
  `;
  const body = grouped.length
    ? grouped.map(group => `
        <div class="notification-group">
          <div class="notification-group-title">${esc(group.label)}</div>
          ${group.items.map(item => renderNotificationItem(item)).join('')}
        </div>
      `).join('')
    : emptyState('No notifications.');
  const html = `${head}<div class="notification-scroll">${body}</div>`;
  panel.innerHTML = html;
  const page = document.getElementById('notifications-list');
  if (page) page.innerHTML = html;
}

function toggleNotifications(force) {
  const panel = document.getElementById('notification-panel');
  if (!panel) return;
  const next = typeof force === 'boolean' ? force : !panel.classList.contains('open');
  panel.classList.toggle('open', next);
  if (next) loadNotifications();
}

async function markNotificationRead(id) {
  await api(`/api/notifications/${id}/read`, { method: 'PATCH' });
  const idx = notificationsCache.findIndex(item => item.id === id);
  if (idx !== -1) {
    notificationsCache[idx].read = true;
    notificationsCache[idx].readAt = new Date().toISOString();
  }
  await loadNotifications();
}

async function markAllNotificationsRead() {
  try {
    const data = await api('/api/notifications/read-all', { method: 'PATCH' });
    setUnreadBadge(Number(data.unread || 0));
    await loadNotifications();
  } catch (err) {
    toast(err.message || 'Failed to update notifications', 'error');
  }
}

function setUnreadBadge(count) {
  const badge = document.getElementById('unread-badge');
  if (!badge) return;
  const safe = Number.isFinite(count) ? count : 0;
  badge.textContent = safe > 0 ? String(safe) : '';
  badge.classList.toggle('has-count', safe > 0);
}

function groupNotifications(items) {
  const groups = new Map();
  items.forEach(item => {
    const label = notificationDayLabel(item.createdAt);
    if (!groups.has(label)) groups.set(label, []);
    groups.get(label).push(item);
  });
  return Array.from(groups.entries()).map(([label, list]) => ({ label, items: list }));
}

function notificationDayLabel(value) {
  if (!value) return 'Earlier';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Earlier';
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfThatDay = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const diffDays = Math.round((startOfToday - startOfThatDay) / 86400000);
  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Yesterday';
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function renderNotificationItem(item) {
  const ts = item?.createdAt ? new Date(item.createdAt) : null;
  const timeText = ts && !Number.isNaN(ts.getTime())
    ? ts.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
    : '';
  const kind = String(item?.type || '').toUpperCase();
  const glyph = {
    SESSION_CONFIRMED: '✅',
    SESSION_CANCELLED: '⚠️',
    FEEDBACK_SUBMITTED: '📝',
    MEETING_LIVE: '🎥',
    SESSION_REMINDER: '⏰',
    PROFILE_VERIFIED: '🔒',
    PASSWORD_UPDATED: '🔑',
    ROLE_UPDATED: '🧭',
  }[kind] || '•';
  const idArg = jsArg(item?.id || '');
  return `
    <button class="notification-item ${item?.read ? '' : 'unread'}" onclick="markNotificationRead(${idArg})">
      <span class="notification-glyph" aria-hidden="true">${glyph}</span>
      <span class="notification-copy">
        <strong>${esc(item?.title || 'Notification')}</strong>
        <span>${esc(item?.message || '')}</span>
      </span>
      <span class="notification-time">${esc(timeText)}</span>
    </button>
  `;
}

function startNotificationStream() {
  stopNotificationStream();
  if (!localStorage.getItem('ip_access_token')) return;
  notificationStreamController = new AbortController();
  streamNotifications(notificationStreamController.signal).catch(() => scheduleNotificationStreamRetry());
}

function stopNotificationStream() {
  if (notificationStreamRetryTimer) clearTimeout(notificationStreamRetryTimer);
  notificationStreamRetryTimer = null;
  if (notificationStreamController) notificationStreamController.abort();
  notificationStreamController = null;
}

function scheduleNotificationStreamRetry() {
  if (notificationStreamRetryTimer) return;
  notificationStreamRetryTimer = setTimeout(() => {
    notificationStreamRetryTimer = null;
    startNotificationStream();
  }, 3500);
}

async function streamNotifications(signal) {
  const token = localStorage.getItem('ip_access_token');
  const res = await fetch(`${API_BASE}/api/notifications/stream`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal,
  });
  if (!res.ok || !res.body) throw new Error('Notification stream unavailable');
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    buffer = consumeSseBuffer(buffer);
  }
  throw new Error('Notification stream closed');
}

function consumeSseBuffer(buffer) {
  const parts = buffer.split('\n\n');
  for (let i = 0; i < parts.length - 1; i += 1) {
    const event = parseSseEvent(parts[i]);
    if (!event) continue;
    if (event.event === 'notification' && event.data) {
      try {
        const payload = JSON.parse(event.data);
        onNotification(payload);
      } catch {}
    }
  }
  return parts[parts.length - 1];
}

function parseSseEvent(chunk) {
  if (!chunk) return null;
  const lines = chunk.split('\n');
  let event = '';
  let data = '';
  lines.forEach(line => {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    if (line.startsWith('data:')) data += `${line.slice(5).trim()}\n`;
  });
  return { event, data: data.trim() };
}

function onNotification(notification) {
  if (!notification?.id) return;
  if (notificationsCache.some(item => item.id === notification.id)) return;
  notificationsCache = [notification, ...(notificationsCache || [])].slice(0, 50);
  const unread = notificationsCache.filter(item => !item.read).length;
  setUnreadBadge(unread);
  const panel = document.getElementById('notification-panel');
  if (panel?.classList.contains('open')) {
    renderNotifications(notificationsCache);
  }
}

function renderProfile() {
  const summary = document.getElementById('profile-summary');
  if (!summary) return;
  const roles = userRoles().map(workspaceLabel).join(', ');
  const isVerified = Boolean(currentUser.isVerified);
  const profileCompletion = normalizedPercent(currentUser.profileCompletion ?? currentUser.profileCompletionPercent);
  const showIntervieweePreferences = hasIntervieweeRole();
  const showInterviewerSetup = hasInterviewerRole();
  const availabilityPreference = intervieweeAvailabilityPreference();
  const availabilityNotes = intervieweeAvailabilityNotes();
  summary.innerHTML = `
    <div class="preview-profile">
      <div id="profile-summary-avatar">${avatarMarkup(currentUser, 'avatar large', currentProfileAvatarUrl())}</div>
      <div>
        <h2>${esc(accountDisplayName(currentUser))}</h2>
        <p>${esc(currentUser.email || '')}</p>
        <p class="identity-handle">@${esc(currentUser.username || 'username')}</p>
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
          <label for="profile-username">Username</label>
          <input id="profile-username" value="${esc(currentUser.username || '')}" required minlength="3" maxlength="24" autocomplete="username" oninput="validateProfileUsernameInput()" />
          <small class="field-hint">Used for login and profile identity</small>
          <small id="profile-username-status" class="field-validation"></small>
        </div>
        <div class="form-group">
          <label for="profile-name">Display name</label>
          <input id="profile-name" value="${esc(accountDisplayName(currentUser))}" required autocomplete="name" />
          <small class="field-hint">Shown publicly on your profile</small>
        </div>
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
          <label for="profile-company">Company</label>
          <input id="profile-company" value="${esc(currentUser.company || '')}" placeholder="OpenAI" />
        </div>
        <div class="form-group">
          <label for="profile-current-role">Current role / headline</label>
          <input id="profile-current-role" value="${esc(currentUser.currentRole || '')}" placeholder="Senior Backend Engineer" />
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
      <div class="form-grid">
        <div class="form-group">
          <label for="profile-years">Years of experience</label>
          <input id="profile-years" type="number" min="0" max="40" value="${esc(String(currentUser.yearsExperience || 0))}" />
        </div>
        <div class="form-group">
          <label for="profile-timezone">Timezone</label>
          <input id="profile-timezone" value="${esc(currentUser.timeZone || '')}" placeholder="Asia/Kolkata" />
        </div>
      </div>
      <div class="form-grid">
        <div class="form-group">
          <label for="profile-topics">Interview topics</label>
          <input id="profile-topics" value="${esc((currentUser.interviewTopics || []).join(', '))}" placeholder="System Design, Behavioral, DSA" />
        </div>
        <div class="form-group">
          <label for="profile-durations">Session durations</label>
          <select id="profile-durations" multiple>
            ${AVAILABILITY_DURATIONS.map(duration => `<option value="${duration}" ${(currentUser.sessionDurations || []).includes(duration) ? 'selected' : ''}>${duration} minutes</option>`).join('')}
          </select>
        </div>
      </div>
      ${showInterviewerSetup ? `
        <div class="availability-preference-card">
          <h3>Public marketplace controls</h3>
          <div class="form-grid">
            <label class="check-row"><input id="profile-accepting-bookings" type="checkbox" ${currentUser.acceptingBookings === false ? '' : 'checked'} /> Accept new bookings</label>
            <label class="check-row"><input id="profile-public-profile" type="checkbox" ${currentUser.publicProfileVisible === false ? '' : 'checked'} /> Show public profile</label>
          </div>
          <div class="profile-link-row">
            <p class="availability-summary-note">Your public profile link: <a href="${esc(publicProfileUrl(currentUser.username || currentUser.id))}" target="_blank" rel="noreferrer">${esc(publicProfileUrl(currentUser.username || currentUser.id))}</a></p>
            <div class="card-actions">
              <button class="btn btn-outline btn-sm" type="button" onclick="copyPublicProfileLink()">Copy link</button>
            </div>
          </div>
        </div>
      ` : ''}
      ${showInterviewerSetup ? `
        <div class="availability-preference-card">
          <h3>Interviewer verification</h3>
          <div class="profile-status-row">
            <span class="badge ${currentUser.interviewerVerified ? 'badge-green' : currentUser.verificationRequestStatus === 'PENDING' ? 'badge-yellow' : currentUser.verificationRequestStatus === 'REJECTED' ? 'badge-red' : 'badge-gray'}">${esc(currentUser.interviewerVerified ? 'Verified interviewer' : currentUser.verificationRequestStatus || 'NONE')}</span>
            ${currentUser.verificationRequestedAt ? `<span class="availability-summary-note">Requested ${esc(fmtDate(currentUser.verificationRequestedAt))}</span>` : ''}
          </div>
          <p class="availability-summary-note">Share verification evidence for manual review. Suspicious or incomplete submissions stay pending until an admin approves them.</p>
          <div class="form-grid">
            <div class="form-group">
              <label for="profile-linkedin-url">LinkedIn or profile URL</label>
              <input id="profile-linkedin-url" value="${esc(currentUser.linkedInUrl || '')}" placeholder="https://linkedin.com/in/your-profile" />
            </div>
            <div class="form-group">
              <label for="profile-company-email">Company email</label>
              <input id="profile-company-email" value="${esc(currentUser.verificationCompanyEmail || '')}" placeholder="name@company.com" />
            </div>
          </div>
          <div class="form-group">
            <label for="profile-verification-notes">Verification notes</label>
            <textarea id="profile-verification-notes" class="compact-textarea" placeholder="Add role, company, or credential context for the moderation team.">${esc(currentUser.verificationRequestNotes || '')}</textarea>
          </div>
          ${currentUser.verificationNotes ? `<p class="admin-note-chip">Admin note: ${esc(currentUser.verificationNotes)}</p>` : ''}
          <div class="card-actions">
            <button class="btn btn-primary btn-sm" type="button" onclick="submitVerificationRequest()">${currentUser.verificationRequestStatus === 'PENDING' ? 'Refresh request' : 'Submit verification request'}</button>
          </div>
        </div>
      ` : ''}
      ${showIntervieweePreferences ? `
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
      ` : ''}
      ${showInterviewerSetup ? `
        <div class="availability-summary-card">
          <h3>Weekly availability</h3>
          <p class="availability-summary-note">Manage recurring interviewer schedules from the interviewer workspace. Your generated slots update automatically there.</p>
          <div class="availability-form-actions">
            <button class="btn btn-outline btn-sm" type="button" onclick="openAvailabilityManager()">Open availability manager</button>
          </div>
        </div>
      ` : ''}
      <button class="btn btn-primary btn-full" id="profile-save-btn">Save profile</button>
    </form>
    <div class="divider"></div>
    ${roleManagementSection()}
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
    <div class="divider"></div>
    ${deleteAccountSection()}
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

function roleManagementSection() {
  const roles = userRoles();
  const hasInterviewee = roles.includes('INTERVIEWEE');
  const hasInterviewer = roles.includes('INTERVIEWER');
  return `
    <section class="profile-management-card">
      <div>
        <h2>Role management</h2>
        <p class="availability-summary-note">Use one account across interviewee and interviewer workspaces. Adding a role keeps your profile, sessions, and settings together.</p>
      </div>
      <div class="role-management-grid">
        <article class="role-option ${hasInterviewer ? 'active' : ''}">
          <div>
            <strong>Interviewer role</strong>
            <p>${hasInterviewer ? 'Active. Set expertise and weekly availability to accept bookings.' : 'Add this role to publish expertise, manage availability, and receive booking requests.'}</p>
          </div>
          ${hasInterviewer
            ? '<span class="badge badge-green">Active</span>'
            : '<button class="btn btn-outline btn-sm" id="add-interviewer-role-btn" type="button" onclick="addRoleToAccount(\'INTERVIEWER\')">Add interviewer role</button>'}
        </article>
        <article class="role-option ${hasInterviewee ? 'active' : ''}">
          <div>
            <strong>Interviewee role</strong>
            <p>${hasInterviewee ? 'Active. Keep scheduling preferences current for better recommendations.' : 'Add this role to book interviewers and keep scheduling preferences on this account.'}</p>
          </div>
          ${hasInterviewee
            ? '<span class="badge badge-green">Active</span>'
            : '<button class="btn btn-outline btn-sm" id="add-interviewee-role-btn" type="button" onclick="addRoleToAccount(\'INTERVIEWEE\')">Add interviewee role</button>'}
        </article>
      </div>
    </section>
  `;
}

function deleteAccountSection() {
  return `
    <section class="profile-management-card danger-zone">
      <div>
        <h2>Delete account</h2>
        <p class="availability-summary-note">This action cannot be undone. Your profile, avatar reference, availability, saved preferences, role associations, sessions, and related feedback will be permanently removed.</p>
      </div>
      <div class="form-grid">
        <div class="form-group">
          <label for="delete-password">Current password</label>
          <input id="delete-password" type="password" autocomplete="current-password" placeholder="Confirm your password" />
        </div>
        <div class="form-group">
          <label for="delete-confirmation">Type DELETE</label>
          <input id="delete-confirmation" autocomplete="off" placeholder="DELETE" />
        </div>
      </div>
      <button class="btn btn-danger btn-full" id="delete-account-btn" type="button" onclick="deleteAccount()">Delete account permanently</button>
    </section>
  `;
}

function initProfileControls() {
  profileUsernameState = { value: normalizeUsernameInput(currentUser.username || ''), available: true };
  setFieldValidation('profile-username-status', '', 'neutral');
  FormUx.initTagInput('profile-skills', { placeholder: 'Add skill or expertise' });
  FormUx.initTagInput('profile-domains', {
    placeholder: 'Add domain',
    label: 'Add preferred interview domain',
    suggestions: DOMAIN_SUGGESTIONS,
    commitOnTab: false,
    suggestionClickCommits: false,
  });
  FormUx.initTagInput('profile-topics', {
    placeholder: 'Add interview topic',
    label: 'Add interview topic',
    suggestions: TOPIC_OPTIONS,
  });
  FormUx.initLanguageSelect('profile-language', { placeholder: 'Search languages' });
  FormUx.initSearchSelect('profile-timezone', {
    placeholder: 'Search timezone',
    label: 'Search timezone',
    options: discoveryFilterOptions.timeZones.length ? discoveryFilterOptions.timeZones : TIMEZONE_SUGGESTIONS,
    preserveCase: true,
  });
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

function selectedProfileDurations() {
  const select = document.getElementById('profile-durations');
  if (!select) return [];
  return Array.from(select.selectedOptions || [])
    .map(option => Number(option.value))
    .filter(value => Number.isFinite(value) && value > 0);
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
      displayName: access.displayName || accountDisplayName(currentUser),
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

function normalizeUsernameInput(value) {
  return String(value || '').trim().toLowerCase();
}

function usernameValidationMessage(username) {
  if (!username) return 'Username is required';
  if (username.length < 3) return 'Username must be at least 3 characters';
  if (username.length > 24) return 'Username must be 24 characters or fewer';
  if (!USERNAME_PATTERN.test(username)) return 'Only lowercase letters, numbers, dots, underscores, and hyphens allowed';
  return '';
}

function setFieldValidation(id, message = '', type = 'neutral') {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = message;
  el.className = `field-validation ${message ? 'show' : ''} ${type ? `field-${type}` : ''}`;
}

function validateProfileUsernameInput() {
  const input = document.getElementById('profile-username');
  if (!input) return;
  const username = normalizeUsernameInput(input.value);
  if (input.value !== username) input.value = username;
  profileUsernameState = { value: username, available: false };
  clearTimeout(profileUsernameTimer);
  const message = usernameValidationMessage(username);
  if (message) {
    setFieldValidation('profile-username-status', message, 'error');
    return;
  }
  if (username === normalizeUsernameInput(currentUser.username || '')) {
    profileUsernameState = { value: username, available: true };
    setFieldValidation('profile-username-status', 'Current username', 'neutral');
    return;
  }
  setFieldValidation('profile-username-status', 'Checking availability...', 'neutral');
  profileUsernameTimer = setTimeout(() => checkProfileUsernameAvailability(username), 320);
}

async function checkProfileUsernameAvailability(username) {
  try {
    const result = await api(`/api/users/username-availability?username=${encodeURIComponent(username)}`);
    profileUsernameState = { value: username, available: Boolean(result.available) };
    setFieldValidation('profile-username-status', result.available ? 'Username available' : 'Username already taken', result.available ? 'success' : 'error');
  } catch (err) {
    profileUsernameState = { value: username, available: false };
    setFieldValidation('profile-username-status', err.message || 'Could not check username', 'error');
  }
}

async function validateProfileUsernameBeforeSubmit() {
  const input = document.getElementById('profile-username');
  const username = normalizeUsernameInput(input?.value);
  if (input && input.value !== username) input.value = username;
  const message = usernameValidationMessage(username);
  if (message) {
    setFieldValidation('profile-username-status', message, 'error');
    return null;
  }
  if (profileUsernameState.value === username && profileUsernameState.available) {
    return username;
  }
  await checkProfileUsernameAvailability(username);
  return profileUsernameState.value === username && profileUsernameState.available ? username : null;
}

async function saveProfile(event) {
  event.preventDefault();
  const btn = document.getElementById('profile-save-btn');
  setButtonLoading(btn, true, 'Saving');
  try {
    const username = await validateProfileUsernameBeforeSubmit();
    if (!username) return;
    const displayName = val('profile-name');
    const payload = {
      name: displayName,
      displayName,
      username,
      bio: val('profile-bio'),
      skills: FormUx.getTagValues('profile-skills'),
      language: FormUx.getLanguageString('profile-language'),
      timeZone: val('profile-timezone'),
      preferredDomains: FormUx.getTagValues('profile-domains'),
      interviewTopics: FormUx.getTagValues('profile-topics'),
      sessionDurations: selectedProfileDurations(),
      experienceLevel: val('profile-experience'),
      company: val('profile-company'),
      currentRole: val('profile-current-role'),
      yearsExperience: Number(val('profile-years') || 0),
      linkedInUrl: val('profile-linkedin-url'),
      verificationCompanyEmail: val('profile-company-email'),
    };
    if (document.getElementById('profile-accepting-bookings')) payload.acceptingBookings = document.getElementById('profile-accepting-bookings').checked;
    if (document.getElementById('profile-public-profile')) payload.publicProfileVisible = document.getElementById('profile-public-profile').checked;
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
    prepHubSignature = '';
    loadPrepHub();
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

async function addRoleToAccount(role) {
  const normalized = String(role || '').toUpperCase();
  const btn = document.getElementById(normalized === 'INTERVIEWER' ? 'add-interviewer-role-btn' : 'add-interviewee-role-btn');
  setButtonLoading(btn, true, 'Adding');
  try {
    const updated = await api('/api/users/me/roles', {
      method: 'POST',
      body: JSON.stringify({ role: normalized }),
    });
    currentUser = updated;
    localStorage.setItem('ip_user', JSON.stringify(updated));
    initUi();
    renderProfile();
    if (normalized === 'INTERVIEWER') await loadAvailabilityManagement(true);
    toast(`${workspaceLabel(normalized)} added to this account.`, 'success');
  } catch (err) {
    toast(err.message || 'Could not add role.', 'error');
  } finally {
    setButtonLoading(btn, false);
  }
}

async function deleteAccount() {
  const confirmation = val('delete-confirmation');
  if (confirmation.toUpperCase() !== 'DELETE') {
    toast('Type DELETE to confirm account deletion.', 'error');
    return;
  }
  if (!window.confirm('This action cannot be undone. Permanently delete your account and related profile data?')) {
    return;
  }
  const btn = document.getElementById('delete-account-btn');
  setButtonLoading(btn, true, 'Deleting');
  try {
    await api('/api/users/me', {
      method: 'DELETE',
      body: JSON.stringify({
        password: document.getElementById('delete-password')?.value || '',
        confirmation,
      }),
    });
    localStorage.removeItem('ip_user');
    localStorage.removeItem('ip_access_token');
    localStorage.removeItem('ip_refresh_token');
    window.location.href = '../index.html';
  } catch (err) {
    toast(err.message || 'Could not delete account.', 'error');
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
  const progressData = topicTrendProgress() || getProgressData();
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

function topicTrendProgress() {
  const trends = analyticsSummary?.topicTrends;
  if (!Array.isArray(trends) || trends.length === 0) return null;
  return trends
    .filter(item => item && item.topic && Number.isFinite(Number(item.averageRating)) && Number(item.averageRating) > 0)
    .slice(0, 8)
    .map(item => ({
      label: String(item.topic),
      percent: normalizedPercent((Number(item.averageRating) / 5) * 100),
    }))
    .filter(item => item.percent != null);
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
  moderationDialogState = null;
  reportDialogState = null;
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
  const name = accountDisplayName(user);
  return name.split(/\s+/).map(part => part[0]).join('').slice(0, 2).toUpperCase();
}

function accountDisplayName(user) {
  return user?.displayName || user?.name || user?.username || user?.email || 'InterviewPrep user';
}

function interviewerName(interviewer) {
  return accountDisplayName(interviewer) || 'Interviewer';
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
  const label = accountDisplayName(user);
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

function publicProfileUrl(username) {
  return new URL(`./interviewer.html?username=${encodeURIComponent(username)}`, window.location.href).toString();
}

function discoveryStorageKey() {
  return `ip_discovery_${currentUser?.id || 'guest'}`;
}

function collectDiscoveryFilters() {
  return {
    q: val('search-q'),
    expertise: val('filter-expertise'),
    company: val('filter-company'),
    language: val('filter-language'),
    timezone: val('filter-timezone'),
    topic: val('filter-topic'),
    years: val('filter-years'),
    level: val('filter-level'),
    rating: val('filter-rating'),
    duration: val('filter-duration'),
    sort: val('filter-sort'),
    available: document.getElementById('filter-available')?.checked || false,
    availableToday: document.getElementById('filter-available-today')?.checked || false,
    free: document.getElementById('filter-free')?.checked || false,
    verified: document.getElementById('filter-verified')?.checked || false,
  };
}

function persistDiscoveryFilters() {
  if (!currentUser?.id) return;
  localStorage.setItem(discoveryStorageKey(), JSON.stringify(collectDiscoveryFilters()));
}

function restoreDiscoveryFilters() {
  if (!currentUser?.id) return;
  const saved = readJson(discoveryStorageKey());
  if (!saved) return;
  const apply = () => {
    setInputValue('search-q', saved.q);
    document.getElementById('filter-expertise')?.__searchSelectControl?.setValue(saved.expertise || '');
    document.getElementById('filter-company')?.__searchSelectControl?.setValue(saved.company || '');
    document.getElementById('filter-language')?.__searchSelectControl?.setValue(saved.language || '');
    document.getElementById('filter-timezone')?.__searchSelectControl?.setValue(saved.timezone || '');
    document.getElementById('filter-topic')?.__searchSelectControl?.setValue(saved.topic || '');
    setInputValue('filter-years', saved.years);
    setInputValue('filter-level', saved.level);
    setInputValue('filter-rating', saved.rating);
    setInputValue('filter-duration', saved.duration);
    setInputValue('filter-sort', saved.sort || 'top-rated');
    if (document.getElementById('filter-available')) document.getElementById('filter-available').checked = Boolean(saved.available);
    if (document.getElementById('filter-available-today')) document.getElementById('filter-available-today').checked = Boolean(saved.availableToday);
    if (document.getElementById('filter-free')) document.getElementById('filter-free').checked = Boolean(saved.free);
    if (document.getElementById('filter-verified')) document.getElementById('filter-verified').checked = Boolean(saved.verified);
  };
  setTimeout(apply, 0);
}

function setInputValue(id, value) {
  const el = document.getElementById(id);
  if (!el) return;
  el.value = value || '';
}

function renderActiveFilterChips() {
  const host = document.getElementById('active-filter-chips');
  if (!host) return;
  const filters = collectDiscoveryFilters();
  const chips = [
    filters.q && ['search-q', `Search: ${filters.q}`],
    filters.expertise && ['filter-expertise', filters.expertise],
    filters.company && ['filter-company', filters.company],
    filters.language && ['filter-language', filters.language],
    filters.timezone && ['filter-timezone', filters.timezone],
    filters.topic && ['filter-topic', filters.topic],
    filters.years && ['filter-years', `${filters.years}+ years`],
    filters.level && ['filter-level', filters.level],
    filters.rating && ['filter-rating', `${filters.rating}+ stars`],
    filters.duration && ['filter-duration', `${filters.duration} min`],
    filters.available && ['filter-available', 'Available'],
    filters.availableToday && ['filter-available-today', 'Today'],
    filters.free && ['filter-free', 'Free'],
    filters.verified && ['filter-verified', 'Verified'],
  ].filter(Boolean);
  host.innerHTML = chips.map(([key, label]) => `<button class="chip active" type="button" onclick="clearDiscoveryFilter('${key}')">${esc(label)} ×</button>`).join('');
  host.classList.toggle('empty', chips.length === 0);
  refreshDiscoverFilterUi();
}

function clearDiscoveryFilter(key) {
  if (key === 'search-q') hideSearchSuggestions();
  const checkbox = document.getElementById(key);
  if (checkbox?.type === 'checkbox') {
    checkbox.checked = false;
  } else if (checkbox?.__searchSelectControl) {
    checkbox.__searchSelectControl.setValue('');
  } else if (checkbox) {
    checkbox.value = '';
  }
  interviewerPage = 0;
  persistDiscoveryFilters();
  renderActiveFilterChips();
  loadInterviewers();
}

function clearAllDiscoveryFilters() {
  ['search-q', 'filter-expertise', 'filter-company', 'filter-language', 'filter-timezone', 'filter-topic', 'filter-years', 'filter-level', 'filter-rating', 'filter-duration', 'filter-sort', 'filter-available', 'filter-available-today', 'filter-free', 'filter-verified']
    .forEach(id => {
      if (id === 'filter-sort') {
        setInputValue(id, 'top-rated');
        return;
      }
      clearDiscoveryFilterValue(id);
    });
  hideSearchSuggestions();
  interviewerPage = 0;
  persistDiscoveryFilters();
  renderActiveFilterChips();
  loadInterviewers();
}

function clearDiscoveryFilterValue(key) {
  const field = document.getElementById(key);
  if (field?.type === 'checkbox') {
    field.checked = false;
  } else if (field?.__searchSelectControl) {
    field.__searchSelectControl.setValue('');
  } else if (field) {
    field.value = '';
  }
}

function toggleDiscoverFilters(forceOpen) {
  discoverFiltersOpen = typeof forceOpen === 'boolean' ? forceOpen : !discoverFiltersOpen;
  refreshDiscoverFilterUi();
}

function refreshDiscoverFilterUi() {
  const panel = document.getElementById('discover-filter-panel');
  const toggle = document.getElementById('discover-filter-toggle');
  const backdrop = document.getElementById('discover-filter-backdrop');
  const head = document.getElementById('discover-filter-head');
  if (!panel || !toggle || !backdrop || !head) return;
  const isMobile = window.innerWidth <= 760;
  const isOpen = isMobile ? discoverFiltersOpen : true;
  panel.classList.toggle('open', isOpen);
  backdrop.classList.toggle('open', isMobile && isOpen);
  head.classList.toggle('open', isMobile && isOpen);
  toggle.setAttribute('aria-expanded', String(isOpen));
  toggle.textContent = isMobile ? (isOpen ? 'Hide filters' : 'Show filters') : 'Filters';
  if (!isOpen) hideSearchSuggestions();
  updateDiscoverFilterSummary();
}

function updateDiscoverFilterSummary() {
  const host = document.getElementById('discover-filter-summary');
  if (!host) return;
  const filters = collectDiscoveryFilters();
  const count = Object.entries(filters).reduce((total, [key, value]) => {
    if (key === 'sort') return total;
    if (typeof value === 'boolean') return total + (value ? 1 : 0);
    return total + (value ? 1 : 0);
  }, 0);
  if (lastDiscoveryResultCount > 0) {
    host.textContent = `${lastDiscoveryResultCount} matches${count ? ` · ${count} active filters` : ''}`;
    return;
  }
  host.textContent = count ? `${count} active filters` : 'All interviewers';
}

function hideSearchSuggestions() {
  const host = document.getElementById('search-suggestions');
  if (!host) return;
  host.hidden = true;
  host.innerHTML = '';
}

async function loadSearchSuggestions() {
  const query = val('search-q').trim();
  const host = document.getElementById('search-suggestions');
  if (!host) return;
  if (window.innerWidth <= 760 && !discoverFiltersOpen) {
    hideSearchSuggestions();
    return;
  }
  if (query.length < 2) {
    hideSearchSuggestions();
    return;
  }
  try {
    const suggestions = (await api(`/api/interviewers/autocomplete?q=${encodeURIComponent(query)}`) || []).slice(0, 8);
    host.innerHTML = suggestions.map(item => `
      <button type="button" class="search-suggestion-item" onclick="applySearchSuggestion(${jsArg(item.label)}, ${jsArg(item.type)})">
        <strong>${esc(item.label)}</strong><span>${esc(item.type)}</span>
      </button>
    `).join('');
    if (!host.innerHTML) {
      host.innerHTML = `<div class="search-suggestion-item"><strong>No quick matches</strong><span>Keep typing to search</span></div>`;
    }
    host.hidden = !host.innerHTML;
  } catch {
    hideSearchSuggestions();
  }
}

function applySearchSuggestion(label, type) {
  const normalizedType = String(type || '');
  if (normalizedType === 'company') {
    document.getElementById('filter-company')?.__searchSelectControl?.setValue(label);
  } else if (normalizedType === 'expertise') {
    document.getElementById('filter-expertise')?.__searchSelectControl?.setValue(label);
  } else if (normalizedType === 'topic') {
    document.getElementById('filter-topic')?.__searchSelectControl?.setValue(label);
  } else {
    setInputValue('search-q', label);
  }
  hideSearchSuggestions();
  interviewerPage = 0;
  persistDiscoveryFilters();
  renderActiveFilterChips();
  loadInterviewers();
}

async function loadPrepHub() {
  if (!hasIntervieweeRole()) {
    prepHub = null;
    prepHubLoading = false;
    prepHubSignature = '';
    renderPrepPanels();
    return;
  }
  const signature = prepProfileSignature();
  if (prepHub && prepHubSignature === signature) {
    renderPrepPanels();
    return;
  }
  const requestId = ++prepHubRequestId;
  prepHubLoading = true;
  renderPrepPanels();
  try {
    const data = await api('/api/prep/hub');
    if (requestId !== prepHubRequestId) return;
    prepHub = data;
    prepHubSignature = signature;
  } catch {
    if (requestId !== prepHubRequestId) return;
    prepHub = null;
  } finally {
    if (requestId !== prepHubRequestId) return;
    prepHubLoading = false;
    renderPrepPanels();
  }
}

function renderResumePanel() {
  const host = document.getElementById('resume-panel');
  if (!host) return;
  const hasResume = Boolean(currentUser?.resumeUrl);
  const updatedLabel = currentUser?.resumeUpdatedAt ? fmtDate(currentUser.resumeUpdatedAt) : '';
  const resumeQuickWin = (prepHub?.quickWins || []).find(item => (item?.tags || []).some(tag => String(tag).toLowerCase().includes('resume')));
  host.innerHTML = `
    <div class="panel-head"><h2>Resume manager</h2>${hasResume ? `<button class="btn btn-outline btn-sm" type="button" onclick="removeResume()">Remove</button>` : ''}</div>
    <p class="availability-summary-note">Upload a resume once and use it to unlock sharper interview preparation signals.</p>
    ${hasResume ? `
      <div class="resume-card">
        <strong>${esc(currentUser.resumeFileName || 'Uploaded resume')}</strong>
        <span>${esc(currentUser.resumeContentType || 'Document')}</span>
        ${updatedLabel ? `<small class="availability-summary-note">Last updated ${esc(updatedLabel)}</small>` : ''}
        <div class="card-actions">
          <a class="btn btn-outline btn-sm" href="${esc(currentUser.resumeUrl)}" target="_blank" rel="noreferrer">Preview</a>
        </div>
      </div>
    ` : '<div class="empty-state"><p>No resume uploaded yet.</p></div>'}
    ${resumeQuickWin ? `<div class="prep-resume-tip"><strong>${esc(resumeQuickWin.title || 'Resume quick win')}</strong><p>${esc(resumeQuickWin.description || '')}</p></div>` : ''}
    <div class="form-group" style="margin-top:1rem;">
      <label for="resume-upload">Upload resume</label>
      <input id="resume-upload" type="file" accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onchange="uploadResume(event)" />
    </div>
  `;
}

function renderPrepPanels() {
  const resourcesHost = document.getElementById('prep-resources-panel');
  const tracksHost = document.getElementById('prep-tracks-panel');
  if (!resourcesHost || !tracksHost) return;
  if (prepHubLoading) {
    resourcesHost.innerHTML = `
      <h2>Preparation resources</h2>
      <div class="prep-skeleton-grid">${skeletonCards(3)}</div>
    `;
    tracksHost.innerHTML = `
      <h2>Interview tracks</h2>
      <div class="prep-skeleton-grid">${skeletonCards(4)}</div>
    `;
    return;
  }
  if (!prepHub) {
    resourcesHost.innerHTML = '<h2>Preparation resources</h2>' + emptyState('Personalized preparation data is not available right now.');
    tracksHost.innerHTML = '<h2>Interview tracks</h2>' + emptyState('Tracks will appear after preparation data syncs.');
    return;
  }
  const summaryTopics = Array.isArray(prepHub.primaryTopics) ? prepHub.primaryTopics : [];
  const summaryCompanies = Array.isArray(prepHub.targetCompanies) ? prepHub.targetCompanies : [];
  const quickWins = Array.isArray(prepHub.quickWins) ? prepHub.quickWins : [];
  const resources = Array.isArray(prepHub.resources) ? prepHub.resources : [];
  resourcesHost.innerHTML = `
    <div class="panel-head"><h2>Preparation resources</h2><span class="badge badge-purple">${esc(prepHub.persona || 'Personalized')}</span></div>
    <div class="prep-summary-grid">
      <article class="prep-summary-card">
        <strong>Primary topics</strong>
        <div class="tag-row">${summaryTopics.length ? summaryTopics.slice(0, 6).map(item => `<span>${esc(item)}</span>`).join('') : '<span>Add profile topics</span>'}</div>
      </article>
      <article class="prep-summary-card">
        <strong>Company focus</strong>
        <div class="tag-row">${summaryCompanies.length ? summaryCompanies.slice(0, 4).map(item => `<span>${esc(item)}</span>`).join('') : '<span>Discover interviewers by company</span>'}</div>
      </article>
    </div>
    <div class="resource-list prep-resource-list">
      ${quickWins.map(item => renderPrepResourceCard(item, true)).join('')}
      ${resources.map(item => renderPrepResourceCard(item, false)).join('')}
    </div>
  `;
  const sections = [
    ['Role-aware tracks', prepHub.roleTracks || []],
    ['Company-specific tracks', prepHub.companyTracks || []],
    ['Coding prep', prepHub.codingTracks || []],
    ['Behavioral prep', prepHub.behavioralTracks || []],
  ];
  tracksHost.innerHTML = sections.map(([title, items]) => `
    <div class="prep-section-block">
      <div class="panel-head"><h2>${esc(title)}</h2></div>
      <div class="prep-track-grid">
        ${items.length ? items.map(item => `
          ${renderPrepTrackCard(item)}
        `).join('') : '<div class="empty-state"><p>No tracks generated for this section yet.</p></div>'}
      </div>
    </div>
  `).join('') || emptyState('No prep tracks are available yet.');
}

function renderPrepTrackCard(item) {
  const progress = clampPercent(item?.progressPercent);
  const stage = item?.stage || prepStage(progress);
  return `
    <article class="prep-track-card prep-card">
      <div class="prep-card-head">
        <strong>${esc(item?.title || 'Preparation track')}</strong>
        <span class="badge badge-gray">${esc(stage)}</span>
      </div>
      <p>${esc(item?.summary || 'Track summary unavailable.')}</p>
      <div class="prep-progress-row">
        <div class="prep-progress"><i style="width:${progress}%"></i></div>
        <span>${progress}%</span>
      </div>
      <div class="tag-row">${(item?.focusAreas || []).slice(0, 6).map(point => `<span>${esc(point)}</span>`).join('')}</div>
      ${(item?.signals || []).length ? `<div class="tag-row subtle">${item.signals.map(signal => `<span>${esc(signal)}</span>`).join('')}</div>` : ''}
    </article>
  `;
}

function renderPrepResourceCard(item, isQuickWin) {
  const progress = clampPercent(item?.progressPercent);
  const badges = Array.isArray(item?.tags) ? item.tags : [];
  return `
    <article class="resource-item prep-resource-card ${isQuickWin ? 'quick-win' : ''}">
      <div class="prep-card-head">
        <strong>${esc(item?.title || 'Prep resource')}</strong>
        <span class="badge ${isQuickWin ? 'badge-green' : 'badge-gray'}">${esc(item?.type || (isQuickWin ? 'Quick win' : 'Resource'))}</span>
      </div>
      <p>${esc(item?.description || '')}</p>
      <div class="prep-progress-row">
        <div class="prep-progress"><i style="width:${progress}%"></i></div>
        <span>${progress}%</span>
      </div>
      <div class="prep-resource-foot">
        <div class="tag-row subtle">${badges.slice(0, 4).map(tag => `<span>${esc(tag)}</span>`).join('')}</div>
        <span class="prep-action-label">${esc(item?.actionLabel || 'Open')}</span>
      </div>
    </article>
  `;
}

function prepProfileSignature() {
  if (!currentUser?.id) return '';
  const data = {
    id: currentUser.id,
    role: currentUser.currentRole || '',
    years: Number(currentUser.yearsExperience || 0),
    skills: (currentUser.skills || []).join(','),
    topics: (currentUser.interviewTopics || []).join(','),
    domains: (currentUser.preferredDomains || []).join(','),
    company: currentUser.company || '',
    availability: (currentUser.availability || []).join(','),
    resumeUrl: currentUser.resumeUrl || '',
    resumeUpdatedAt: currentUser.resumeUpdatedAt || '',
  };
  return JSON.stringify(data);
}

function prepStage(progress) {
  if (progress >= 75) return 'Strong';
  if (progress >= 50) return 'Building';
  if (progress >= 30) return 'Foundation';
  return 'Starting';
}

function clampPercent(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return 0;
  return Math.max(0, Math.min(100, Math.round(number)));
}

async function uploadResume(event) {
  const file = event?.target?.files?.[0];
  if (!file) return;
  const formData = new FormData();
  formData.append('file', file);
  try {
    currentUser = await api('/api/users/me/resume', { method: 'POST', body: formData });
    localStorage.setItem('ip_user', JSON.stringify(currentUser));
    toast('Resume uploaded.', 'success');
    prepHubSignature = '';
    renderResumePanel();
    loadPrepHub();
  } catch (err) {
    toast(err.message || 'Could not upload resume.', 'error');
  }
}

async function removeResume() {
  try {
    currentUser = await api('/api/users/me/resume', { method: 'DELETE' });
    localStorage.setItem('ip_user', JSON.stringify(currentUser));
    toast('Resume removed.', 'success');
    prepHubSignature = '';
    renderResumePanel();
    loadPrepHub();
  } catch (err) {
    toast(err.message || 'Could not remove resume.', 'error');
  }
}

async function loadAdminData(force = false) {
  if (!hasAdminRole()) return;
  if (!force && adminOverview && adminUsers.length && adminSessions.length) {
    renderAdminPanels();
    return;
  }
  adminLoading = true;
  renderAdminPanels();
  try {
    const [overview, users, sessionsData, reports, reviews] = await Promise.all([
      api('/api/admin/overview'),
      api('/api/admin/users'),
      api('/api/admin/sessions'),
      api('/api/admin/reports'),
      api('/api/admin/reviews'),
    ]);
    adminOverview = overview;
    adminUsers = Array.isArray(users) ? users : [];
    adminSessions = Array.isArray(sessionsData) ? sessionsData : [];
    adminReports = Array.isArray(reports) ? reports : [];
    adminReviews = Array.isArray(reviews) ? reviews : [];
  } catch (err) {
    toast(err.message || 'Could not load admin data.', 'error');
  } finally {
    adminLoading = false;
    renderAdminPanels();
  }
}

function renderAdminPanels() {
  if (!hasAdminRole()) return;
  const metricsHost = document.getElementById('admin-metrics-grid');
  const analyticsHost = document.getElementById('admin-analytics-panel');
  const liveHost = document.getElementById('admin-live-sessions-panel');
  const moderationSummary = adminReports.reduce((acc, item) => {
    const status = String(item?.status || 'OPEN').toUpperCase();
    if (status === 'ACTIONED') acc.actioned += 1;
    else if (status === 'REVIEWED') acc.reviewed += 1;
    else acc.open += 1;
    return acc;
  }, { open: 0, reviewed: 0, actioned: 0 });
  const enabledUsers = Number(adminOverview?.enabledUsers || 0);
  const disabledUsers = Math.max(0, Number(adminOverview?.totalUsers || 0) - enabledUsers);
  const verificationRate = Number(adminOverview?.totalInterviewers || 0) > 0
    ? Math.round((Number(adminOverview?.verifiedInterviewers || 0) * 1000) / Number(adminOverview?.totalInterviewers || 1)) / 10
    : 0;
  if (adminLoading && metricsHost && !adminOverview) {
    metricsHost.innerHTML = skeletonCards(4);
  }
  if (metricsHost && adminOverview) {
    metricsHost.innerHTML = [
      ['Users', adminOverview.totalUsers, 'Accounts on platform'],
      ['Enabled', enabledUsers, 'Can access platform'],
      ['Disabled', disabledUsers, 'Restricted accounts'],
      ['Interviewers', adminOverview.totalInterviewers, 'Total coach profiles'],
      ['Verified', adminOverview.verifiedInterviewers, `${verificationRate}% verified rate`],
      ['Admins', adminOverview.totalAdmins, 'Operational owners'],
      ['Open Reports', moderationSummary.open, 'Needs moderation'],
      ['Actioned Reports', moderationSummary.actioned, 'Confirmed enforcement'],
      ['Public Reviews', adminOverview.visiblePublicReviews, 'Visible on public pages'],
      ['Hidden Reviews', adminOverview.hiddenPublicReviews, 'Suppressed via moderation'],
      ['Sessions', adminOverview.totalSessions, 'Tracked interviews'],
      ['Completion Rate', `${adminOverview.completionRate}%`, 'Completion health'],
      ['Cancellation Rate', `${adminOverview.cancellationRate}%`, 'Reliability risk'],
    ].map(([label, value, meta]) => `
      <div class="stat-card admin-stat-card">
        <span>${esc(String(value))}</span>
        <p>${esc(label)}</p>
        <small>${esc(meta || '')}</small>
      </div>
    `).join('');
  }
  if (analyticsHost) {
    analyticsHost.innerHTML = adminLoading && !adminOverview
      ? skeletonCards(2)
      : adminOverview ? `
        <div class="panel-head"><h2>Platform analytics</h2><span class="badge badge-gray">${esc(String(adminOverview.totalSessions || 0))} sessions tracked</span></div>
        <div class="admin-analytics-stack">
          <section class="admin-analytics-section">
            <h3>Platform metrics</h3>
            <div class="admin-analytics-copy">
              <div class="admin-analytics-metric"><span>Average public rating</span><strong>${esc(String(adminOverview.platformAverageRating || 0))}</strong></div>
              <div class="admin-analytics-metric"><span>Completion rate</span><strong>${esc(String(adminOverview.completionRate || 0))}%</strong></div>
              <div class="admin-analytics-metric"><span>Cancellation rate</span><strong>${esc(String(adminOverview.cancellationRate || 0))}%</strong></div>
            </div>
          </section>
          <section class="admin-analytics-section">
            <h3>Moderation metrics</h3>
            <div class="admin-analytics-copy">
              <div class="admin-analytics-metric"><span>Open reports</span><strong>${esc(String(moderationSummary.open))}</strong></div>
              <div class="admin-analytics-metric"><span>Reviewed reports</span><strong>${esc(String(moderationSummary.reviewed))}</strong></div>
              <div class="admin-analytics-metric"><span>Actioned reports</span><strong>${esc(String(moderationSummary.actioned))}</strong></div>
              <div class="admin-analytics-metric"><span>Visible reviews</span><strong>${esc(String(adminOverview.visiblePublicReviews || 0))}</strong></div>
              <div class="admin-analytics-metric"><span>Hidden reviews</span><strong>${esc(String(adminOverview.hiddenPublicReviews || 0))}</strong></div>
              <div class="admin-analytics-metric"><span>Queue pressure</span><strong>${esc(moderationSummary.open > 8 ? 'High' : moderationSummary.open > 3 ? 'Medium' : 'Low')}</strong></div>
            </div>
          </section>
          <section class="admin-analytics-section">
            <h3>User and session summary</h3>
            <div class="admin-analytics-copy">
              <div class="admin-analytics-metric"><span>Enabled users</span><strong>${esc(String(enabledUsers))}</strong></div>
              <div class="admin-analytics-metric"><span>Disabled users</span><strong>${esc(String(disabledUsers))}</strong></div>
              <div class="admin-analytics-metric"><span>Verification rate</span><strong>${esc(String(verificationRate))}%</strong></div>
              <div class="admin-analytics-metric"><span>Completed sessions</span><strong>${esc(String(adminOverview.completedSessions || 0))}</strong></div>
              <div class="admin-analytics-metric"><span>Pending sessions</span><strong>${esc(String(adminOverview.pendingSessions || 0))}</strong></div>
              <div class="admin-analytics-metric"><span>Cancelled sessions</span><strong>${esc(String(adminOverview.cancelledSessions || 0))}</strong></div>
            </div>
          </section>
        </div>
        <h3 class="admin-topic-title">Top interview topics</h3>
        <div class="admin-topic-bars">
          ${(adminOverview.topTopics || []).map(item => `
            <div class="admin-topic-bar">
              <span>${esc(item.topic)}</span>
              <div><i style="width:${Math.max(10, Math.min(100, Number(item.count || 0) * 10))}%"></i></div>
              <strong>${esc(String(item.count || 0))}</strong>
            </div>
          `).join('') || '<div class="empty-state"><p>No topic analytics yet.</p></div>'}
        </div>
      ` : emptyState('Admin analytics unavailable.');
  }
  if (liveHost) {
    liveHost.innerHTML = adminLoading && !adminSessions.length
      ? skeletonCards(2)
      : `
        <div class="panel-head"><h2>Upcoming session monitoring</h2><span class="badge badge-gray">${esc(String(adminSessions.length || 0))} total</span></div>
        <div class="admin-list">
          ${adminSessions.slice(0, 6).map(session => `
            <article class="admin-list-item">
              <div>
                <strong>${esc(sessionTitle(session))}</strong>
                <p>${esc(fmtDate(session.startTime))}</p>
                <small>${esc(providerLabel(session.meetingProvider))}</small>
              </div>
              <span class="badge badge-${statusClass((session.status || '').toUpperCase())}">${esc(session.status || 'PENDING')}</span>
            </article>
          `).join('') || emptyState('No sessions to monitor right now.')}
        </div>
      `;
  }
  renderAdminUsers();
  renderAdminSessions();
  renderAdminReports();
}

function renderAdminUsers() {
  const host = document.getElementById('admin-users-panel');
  if (!host) return;
  const query = String(document.getElementById('admin-user-search')?.value || '').trim().toLowerCase();
  const role = String(document.getElementById('admin-user-role')?.value || '').trim().toUpperCase();
  const enabled = String(document.getElementById('admin-user-enabled')?.value || '').trim().toLowerCase();
  if (adminLoading && !adminUsers.length) {
    host.innerHTML = skeletonCards(2);
    return;
  }
  const rows = adminUsers.filter(user => {
    const roleMatch = !role || (Array.isArray(user.roles) ? user.roles : [user.role]).includes(role);
    const searchMatch = !query || [user.username, user.displayName, user.email, user.company].some(value => String(value || '').toLowerCase().includes(query));
    const enabledMatch = !enabled || (enabled === 'enabled' ? user.accountEnabled !== false : user.accountEnabled === false);
    return roleMatch && enabledMatch && searchMatch;
  });
  const page = paginateCollection(rows, adminState.users);
  host.innerHTML = `
    <div class="admin-table-shell">
      <div class="admin-table-meta"><strong>${esc(String(rows.length))}</strong><span>matching users</span></div>
      <div class="admin-table">
      ${page.items.map(user => `
        <article class="admin-table-row">
          <div>
            <strong>${esc(accountDisplayName(user))}</strong>
            <p>${esc(user.email || '')}</p>
            <div class="tag-row">
              ${(user.roles || [user.role]).map(roleName => `<span>${esc(roleName)}</span>`).join('')}
              ${user.interviewerVerified ? '<span>Verified interviewer</span>' : ''}
            </div>
          </div>
          <div class="card-actions">
            ${user.roles?.includes?.('INTERVIEWER') ? `<button class="btn btn-outline btn-sm" type="button" onclick="toggleInterviewerVerification('${user.id}', ${user.interviewerVerified ? 'false' : 'true'})">${user.interviewerVerified ? 'Unverify' : 'Verify'}</button>` : ''}
            <button class="btn btn-outline btn-sm" type="button" onclick="togglePublicProfileVisibility('${user.id}', ${user.publicProfileVisible === false ? 'true' : 'false'})">${user.publicProfileVisible === false ? 'Show profile' : 'Hide profile'}</button>
            <button class="btn ${user.accountEnabled === false ? 'btn-success' : 'btn-danger'} btn-sm" type="button" onclick="toggleUserAccess('${user.id}', ${user.accountEnabled === false ? 'true' : 'false'})">${user.accountEnabled === false ? 'Reactivate' : 'Deactivate'}</button>
          </div>
        </article>
      `).join('') || emptyState('No users match the current filters.')}
      </div>
      ${renderAdminPagination('users', page)}
    </div>
  `;
}

function renderAdminSessions() {
  const host = document.getElementById('admin-sessions-panel');
  if (!host) return;
  if (adminLoading && !adminSessions.length) {
    host.innerHTML = skeletonCards(3);
    return;
  }
  const query = String(document.getElementById('admin-session-search')?.value || '').trim().toLowerCase();
  const status = String(document.getElementById('admin-session-status')?.value || '').trim().toUpperCase();
  const rows = adminSessions.filter(session => {
    const statusMatch = !status || String(session.status || '').toUpperCase() === status;
    const searchText = [sessionTitle(session), providerLabel(session.meetingProvider), ...(sessionTopics(session) || [])].join(' ').toLowerCase();
    return statusMatch && (!query || searchText.includes(query));
  });
  const page = paginateCollection(rows, adminState.sessions);
  host.innerHTML = `
    <div class="admin-table-shell">
      <div class="admin-table-meta"><strong>${esc(String(rows.length))}</strong><span>matching sessions</span></div>
      <div class="admin-table">
      ${page.items.map(session => `
        <article class="admin-table-row">
          <div>
            <strong>${esc(sessionTitle(session))}</strong>
            <p>${esc(fmtDate(session.startTime))}</p>
            <div class="tag-row">${sessionTopics(session).map(topic => `<span>${esc(topic)}</span>`).join('')}</div>
          </div>
          <div class="card-actions">
            <span class="badge badge-${statusClass((session.status || '').toUpperCase())}">${esc(session.status || 'PENDING')}</span>
            <span class="badge badge-gray">${esc(providerLabel(session.meetingProvider))}</span>
          </div>
        </article>
      `).join('') || emptyState('No sessions found.')}
      </div>
      ${renderAdminPagination('sessions', page)}
    </div>
  `;
}

function renderAdminReports() {
  const host = document.getElementById('admin-reports-panel');
  if (!host) return;
  if (adminLoading && !adminReports.length && !adminReviews.length) {
    host.innerHTML = skeletonCards(3);
    return;
  }
  const reportQuery = String(adminState.reports.query || '').trim().toLowerCase();
  const reportStatus = String(adminState.reports.status || '').trim().toUpperCase();
  const reportCategory = String(adminState.reports.category || '').trim().toUpperCase();
  const filteredReports = adminReports.filter(report => {
    const statusMatch = !reportStatus || String(report.status || '').toUpperCase() === reportStatus;
    const categoryValue = normalizeReportCategory(report.reason);
    const categoryMatch = !reportCategory || categoryValue === reportCategory;
    const searchText = [report.reason, report.details, report.reporterId, report.reportedUserId, report.reviewedByAdminId].filter(Boolean).join(' ').toLowerCase();
    return statusMatch && categoryMatch && (!reportQuery || searchText.includes(reportQuery));
  });
  const reportPage = paginateCollection(filteredReports, adminState.reports);
  const reviewQuery = String(adminState.reviews.query || '').trim().toLowerCase();
  const visibility = String(adminState.reviews.visibility || '').trim().toLowerCase();
  const minRating = Number(adminState.reviews.minRating || 0);
  const filteredReviews = adminReviews.filter(review => {
    const visibilityMatch = !visibility || (visibility === 'visible' ? review.publicReview === true : review.publicReview !== true);
    const ratingMatch = !minRating || Number(review.rating || 0) >= minRating;
    const topicText = Array.isArray(review.topicSummaries) ? review.topicSummaries.map(item => item.topic).join(' ') : '';
    const searchText = [review.reviewerName, review.interviewerName, review.comments, review.sessionTitle, topicText, review.moderationNotes].filter(Boolean).join(' ').toLowerCase();
    return visibilityMatch && ratingMatch && (!reviewQuery || searchText.includes(reviewQuery));
  });
  const reviewPage = paginateCollection(filteredReviews, adminState.reviews);
  const reportEmpty = reportQuery || reportStatus || reportCategory
    ? 'No trust reports match the current filters.'
    : 'No moderation reports queued.';
  const reviewEmpty = reviewQuery || visibility || minRating
    ? 'No review items match the current filters.'
    : 'No public reviews need moderation right now.';
  host.innerHTML = `
    <div class="admin-moderation-grid">
      <article class="admin-moderation-card">
        <div class="panel-head"><h2>Trust reports</h2><span class="badge badge-gray">${esc(String(filteredReports.length))}</span></div>
        ${adminLoading ? '<p class="admin-loading-note">Syncing moderation queue...</p>' : ''}
        <div class="admin-inline-filters">
          <input class="input" value="${esc(adminState.reports.query || '')}" placeholder="Search reports" oninput="setAdminFilter('reports', 'query', this.value)" />
          <select class="input" onchange="setAdminFilter('reports', 'status', this.value)">
            <option value="">All statuses</option>
            <option value="OPEN" ${reportStatus === 'OPEN' ? 'selected' : ''}>Open</option>
            <option value="REVIEWED" ${reportStatus === 'REVIEWED' ? 'selected' : ''}>Reviewed</option>
            <option value="ACTIONED" ${reportStatus === 'ACTIONED' ? 'selected' : ''}>Actioned</option>
          </select>
          <select class="input" onchange="setAdminFilter('reports', 'category', this.value)">
            <option value="">All categories</option>
            ${REPORT_CATEGORY_OPTIONS.map(option => `<option value="${esc(option.value)}" ${reportCategory === option.value ? 'selected' : ''}>${esc(option.label)}</option>`).join('')}
          </select>
        </div>
        <div class="admin-table">
          ${reportPage.items.map(report => `
            <article class="admin-table-row">
              <div class="admin-row-main">
                <div class="admin-row-head">
                  <strong>Trust report</strong>
                  <span class="badge badge-gray">${esc(reportCategoryLabel(report.reason))}</span>
                </div>
                <p>${esc(report.details || 'No details provided.')}</p>
                <small>Reporter ${esc(report.reporterId || 'unknown')} • Target ${esc(report.reportedUserId || 'unknown')}</small>
                <small>${esc(fmtDate(report.createdAt))}${report.reviewedByAdminId ? ` • Moderated by ${esc(report.reviewedByAdminId)}` : ''}</small>
                ${normalizeReportCategory(report.reason) === 'OTHER' && report.reason ? `<small>Original reason: ${esc(report.reason)}</small>` : ''}
                ${report.resolutionNotes ? `<p class="admin-note-chip">Last note: ${esc(report.resolutionNotes)}</p>` : ''}
              </div>
              <div class="card-actions">
                <span class="badge badge-${report.status === 'OPEN' ? 'yellow' : report.status === 'ACTIONED' ? 'red' : 'gray'}">${esc(report.status || 'OPEN')}</span>
                <button class="btn btn-outline btn-sm" type="button" onclick="openReportModerationModal('${report.id}', 'REVIEWED')">Review</button>
                <button class="btn btn-danger btn-sm" type="button" onclick="openReportModerationModal('${report.id}', 'ACTIONED')">Action</button>
              </div>
            </article>
          `).join('') || emptyState(reportEmpty)}
        </div>
        ${renderAdminPagination('reports', reportPage)}
      </article>
      <article class="admin-moderation-card">
        <div class="panel-head"><h2>Public review moderation</h2><span class="badge badge-gray">${esc(String(filteredReviews.length))}</span></div>
        ${adminLoading ? '<p class="admin-loading-note">Refreshing review moderation queue...</p>' : ''}
        <div class="admin-inline-filters">
          <input class="input" value="${esc(adminState.reviews.query || '')}" placeholder="Search reviews" oninput="setAdminFilter('reviews', 'query', this.value)" />
          <select class="input" onchange="setAdminFilter('reviews', 'visibility', this.value)">
            <option value="">All visibility</option>
            <option value="visible" ${visibility === 'visible' ? 'selected' : ''}>Visible</option>
            <option value="hidden" ${visibility === 'hidden' ? 'selected' : ''}>Hidden</option>
          </select>
          <select class="input" onchange="setAdminFilter('reviews', 'minRating', this.value)">
            <option value="">Any rating</option>
            <option value="4" ${String(adminState.reviews.minRating || '') === '4' ? 'selected' : ''}>4+ rating</option>
            <option value="3" ${String(adminState.reviews.minRating || '') === '3' ? 'selected' : ''}>3+ rating</option>
            <option value="2" ${String(adminState.reviews.minRating || '') === '2' ? 'selected' : ''}>2+ rating</option>
          </select>
        </div>
        <div class="admin-table">
          ${reviewPage.items.map(review => `
            <article class="admin-table-row admin-review-row">
              <div class="admin-row-main">
                <div class="admin-row-head">
                  <strong>${esc(review.reviewerName || 'InterviewPrep member')} → ${esc(review.interviewerName || 'Interviewer')}</strong>
                  ${review.interviewerVerified ? '<span class="badge badge-green">Verified</span>' : '<span class="badge badge-gray">Unverified</span>'}
                </div>
                <p>${esc(review.comments || 'No written review provided.')}</p>
                ${Array.isArray(review.topicSummaries) && review.topicSummaries.length ? `
                  <div class="admin-topic-feedback-grid">
                    ${review.topicSummaries.map(topic => `
                      <details class="admin-topic-feedback-card">
                        <summary>${esc(topic.topic || 'Topic')} ${topic.rating ? `<strong>${esc(String(topic.rating))}/5</strong>` : ''}</summary>
                        ${(topic.skillRatings && Object.keys(topic.skillRatings).length)
                          ? `<div class="tag-row subtle">${Object.entries(topic.skillRatings).map(([name, value]) => `<span>${esc(name)} ${esc(String(value || 0))}/5</span>`).join('')}</div>`
                          : ''}
                        ${topic.strengths ? `<p><strong>Strength:</strong> ${esc(topic.strengths)}</p>` : ''}
                        ${topic.improvementAreas ? `<p><strong>Improve:</strong> ${esc(topic.improvementAreas)}</p>` : ''}
                      </details>
                    `).join('')}
                  </div>
                ` : ''}
                <small>${esc(review.sessionTitle || 'Interview session')} • ${esc(fmtDate(review.createdAt))}</small>
                ${review.interviewerReliability != null ? `<small>Reliability ${esc(String(review.interviewerReliability))}%${review.interviewerCancelledSessions != null ? ` • Cancelled ${esc(String(review.interviewerCancelledSessions))}` : ''}</small>` : ''}
                ${review.moderationNotes ? `<p class="admin-note-chip">Last note: ${esc(review.moderationNotes)}</p>` : ''}
              </div>
              <div class="card-actions">
                <span class="badge badge-${review.publicReview ? 'green' : 'gray'}">${review.publicReview ? 'Visible' : 'Hidden'}</span>
                <span class="badge badge-gray">${esc(String(review.rating || 0))}/5</span>
                <button class="btn btn-outline btn-sm" type="button" onclick="openReviewModerationModal('${review.id}', ${review.publicReview ? 'false' : 'true'})">${review.publicReview ? 'Hide' : 'Republish'}</button>
              </div>
            </article>
          `).join('') || emptyState(reviewEmpty)}
        </div>
        ${renderAdminPagination('reviews', reviewPage)}
      </article>
    </div>
  `;
}

async function toggleUserAccess(userId, enabled) {
  try {
    await api(`/api/admin/users/${userId}/moderation`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    });
    toast('User access updated.', 'success');
    await loadAdminData(true);
  } catch (err) {
    toast(err.message || 'Could not update user access.', 'error');
  }
}

async function togglePublicProfileVisibility(userId, publicProfileVisible) {
  try {
    await api(`/api/admin/users/${userId}/moderation`, {
      method: 'PATCH',
      body: JSON.stringify({ publicProfileVisible }),
    });
    toast('Public profile visibility updated.', 'success');
    await loadAdminData(true);
  } catch (err) {
    toast(err.message || 'Could not update profile visibility.', 'error');
  }
}

async function toggleInterviewerVerification(userId, verified) {
  try {
    await api(`/api/admin/interviewers/${userId}/verify`, {
      method: 'PATCH',
      body: JSON.stringify({ verified }),
    });
    toast('Interviewer verification updated.', 'success');
    await loadAdminData(true);
  } catch (err) {
    toast(err.message || 'Could not update interviewer verification.', 'error');
  }
}

function normalizeReportCategory(value) {
  const normalized = String(value || '').trim().toUpperCase();
  if (REPORT_CATEGORY_OPTIONS.some(item => item.value === normalized)) return normalized;
  if (normalized.includes('SPAM') || normalized.includes('SCAM')) return 'SPAM';
  if (normalized.includes('NO-SHOW') || normalized.includes('NO SHOW') || normalized.includes('LATE')) return 'NO_SHOW';
  if (normalized.includes('SAFETY') || normalized.includes('HARASS') || normalized.includes('ABUSE')) return 'SAFETY';
  if (normalized.includes('PROFILE') || normalized.includes('FAKE') || normalized.includes('IMPERSON')) return 'PROFILE';
  if (normalized.includes('QUALITY') || normalized.includes('UNPROFESSIONAL')) return 'QUALITY';
  return 'OTHER';
}

function reportCategoryLabel(value) {
  const category = normalizeReportCategory(value);
  return REPORT_CATEGORY_OPTIONS.find(item => item.value === category)?.label || 'Other trust concern';
}

function reportCategoryHint(value) {
  const category = normalizeReportCategory(value);
  return REPORT_CATEGORY_OPTIONS.find(item => item.value === category)?.hint || '';
}

function openReportModerationModal(reportId, suggestedStatus = 'REVIEWED') {
  const report = adminReports.find(item => item.id === reportId);
  if (!report) {
    toast('Report not found in current queue.', 'error');
    return;
  }
  moderationDialogState = {
    kind: 'report',
    step: 'form',
    loading: false,
    reportId,
    status: String(suggestedStatus || report.status || 'REVIEWED').toUpperCase(),
    notes: report.resolutionNotes || '',
    confirmChecked: false,
  };
  renderModerationDialog();
}

function openReviewModerationModal(reviewId, visible) {
  const review = adminReviews.find(item => item.id === reviewId);
  if (!review) {
    toast('Review not found in current queue.', 'error');
    return;
  }
  moderationDialogState = {
    kind: 'review',
    step: 'form',
    loading: false,
    reviewId,
    visible: visible !== false,
    notes: review.moderationNotes || '',
    confirmChecked: false,
  };
  renderModerationDialog();
}

function resolveAdminReport(reportId, status) {
  openReportModerationModal(reportId, status);
}

function toggleAdminReviewVisibility(reviewId, visible) {
  openReviewModerationModal(reviewId, visible);
}

function updateModerationDialogField(field, value) {
  if (!moderationDialogState) return;
  moderationDialogState[field] = value;
  renderModerationDialog();
}

function continueModerationDialog() {
  if (!moderationDialogState) return;
  if (moderationDialogState.kind === 'report') {
    if (!moderationDialogState.status) {
      toast('Select a moderation decision.', 'error');
      return;
    }
    if (moderationDialogState.status === 'ACTIONED' && String(moderationDialogState.notes || '').trim().length < 8) {
      toast('Add clear action notes (at least 8 characters).', 'error');
      return;
    }
  } else if (!moderationDialogState.visible && String(moderationDialogState.notes || '').trim().length < 8) {
    toast('Add moderation notes before hiding a review.', 'error');
    return;
  }
  if (!moderationDialogState.confirmChecked) {
    toast('Confirm the moderation action before continuing.', 'error');
    return;
  }
  moderationDialogState.step = 'confirm';
  renderModerationDialog();
}

function backModerationDialog() {
  if (!moderationDialogState) return;
  moderationDialogState.step = 'form';
  renderModerationDialog();
}

function closeModerationDialog() {
  moderationDialogState = null;
  closeModal();
}

function renderModerationDialog() {
  if (!moderationDialogState) return;
  if (moderationDialogState.kind === 'report') {
    const report = adminReports.find(item => item.id === moderationDialogState.reportId);
    if (!report) {
      closeModerationDialog();
      return;
    }
    if (moderationDialogState.step === 'confirm') {
      modal(`
        <div class="modal-head">
          <h2>Confirm report moderation</h2>
          <p class="muted">This decision will be saved to the trust queue immediately.</p>
        </div>
        <div class="moderation-summary-list">
          <div><span>Category</span><strong>${esc(reportCategoryLabel(report.reason))}</strong></div>
          <div><span>Decision</span><strong>${esc(moderationDialogState.status)}</strong></div>
          <div><span>Reporter → Target</span><strong>${esc(report.reporterId || 'unknown')} → ${esc(report.reportedUserId || 'unknown')}</strong></div>
          <div><span>Notes</span><strong>${esc(moderationDialogState.notes || 'No notes added')}</strong></div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" type="button" onclick="backModerationDialog()">Back</button>
          <button class="btn btn-primary" type="button" onclick="submitModerationDialog()" ${moderationDialogState.loading ? 'disabled' : ''}>${moderationDialogState.loading ? 'Saving...' : 'Confirm moderation'}</button>
        </div>
      `);
      return;
    }
    modal(`
      <div class="modal-head">
        <h2>Moderate trust report</h2>
        <p class="muted">${esc(reportCategoryHint(report.reason) || 'Review details, then choose a moderation outcome.')}</p>
      </div>
      <div class="moderation-form-grid">
        <label class="form-group">
          <span>Report category</span>
          <input class="input" value="${esc(reportCategoryLabel(report.reason))}" readonly />
        </label>
        <label class="form-group">
          <span>Decision</span>
          <select class="input" onchange="updateModerationDialogField('status', this.value)">
            <option value="OPEN" ${moderationDialogState.status === 'OPEN' ? 'selected' : ''}>Keep open</option>
            <option value="REVIEWED" ${moderationDialogState.status === 'REVIEWED' ? 'selected' : ''}>Reviewed</option>
            <option value="ACTIONED" ${moderationDialogState.status === 'ACTIONED' ? 'selected' : ''}>Actioned</option>
          </select>
        </label>
        <label class="form-group">
          <span>Moderation notes</span>
          <textarea id="moderation-report-notes" placeholder="Document evidence, policy alignment, and next steps." oninput="updateModerationDialogField('notes', this.value)">${esc(moderationDialogState.notes || '')}</textarea>
        </label>
        <label class="check-row">
          <input type="checkbox" ${moderationDialogState.confirmChecked ? 'checked' : ''} onchange="updateModerationDialogField('confirmChecked', this.checked)" />
          I confirm this report decision is accurate.
        </label>
      </div>
      <div class="modal-actions">
        <button class="btn btn-outline" type="button" onclick="closeModerationDialog()">Cancel</button>
        <button class="btn btn-primary" type="button" onclick="continueModerationDialog()">Continue</button>
      </div>
    `);
    return;
  }
  const review = adminReviews.find(item => item.id === moderationDialogState.reviewId);
  if (!review) {
    closeModerationDialog();
    return;
  }
  if (moderationDialogState.step === 'confirm') {
    modal(`
      <div class="modal-head">
        <h2>Confirm review moderation</h2>
        <p class="muted">Visibility changes apply to public profile pages instantly.</p>
      </div>
      <div class="moderation-summary-list">
        <div><span>Reviewer</span><strong>${esc(review.reviewerName || 'InterviewPrep member')}</strong></div>
        <div><span>Interviewer</span><strong>${esc(review.interviewerName || 'Interviewer')}</strong></div>
        <div><span>Visibility</span><strong>${moderationDialogState.visible ? 'Visible' : 'Hidden'}</strong></div>
        <div><span>Notes</span><strong>${esc(moderationDialogState.notes || 'No notes added')}</strong></div>
      </div>
      <div class="modal-actions">
        <button class="btn btn-outline" type="button" onclick="backModerationDialog()">Back</button>
        <button class="btn btn-primary" type="button" onclick="submitModerationDialog()" ${moderationDialogState.loading ? 'disabled' : ''}>${moderationDialogState.loading ? 'Saving...' : 'Confirm moderation'}</button>
      </div>
    `);
    return;
  }
  modal(`
    <div class="modal-head">
      <h2>Moderate public review</h2>
      <p class="muted">Capture context for auditability before changing visibility.</p>
    </div>
    <div class="moderation-form-grid">
      <label class="form-group">
        <span>Current review</span>
        <input class="input" value="${esc(review.sessionTitle || 'Interview session')}" readonly />
      </label>
      <label class="form-group">
        <span>Visibility decision</span>
        <select class="input" onchange="updateModerationDialogField('visible', this.value === 'true')">
          <option value="true" ${moderationDialogState.visible ? 'selected' : ''}>Visible</option>
          <option value="false" ${!moderationDialogState.visible ? 'selected' : ''}>Hidden</option>
        </select>
      </label>
      <label class="form-group">
        <span>Moderation notes</span>
        <textarea id="moderation-review-notes" placeholder="Why is this review visible or hidden?" oninput="updateModerationDialogField('notes', this.value)">${esc(moderationDialogState.notes || '')}</textarea>
      </label>
      <label class="check-row">
        <input type="checkbox" ${moderationDialogState.confirmChecked ? 'checked' : ''} onchange="updateModerationDialogField('confirmChecked', this.checked)" />
        I confirm this visibility update follows trust policy.
      </label>
    </div>
    <div class="modal-actions">
      <button class="btn btn-outline" type="button" onclick="closeModerationDialog()">Cancel</button>
      <button class="btn btn-primary" type="button" onclick="continueModerationDialog()">Continue</button>
    </div>
  `);
}

async function submitModerationDialog() {
  if (!moderationDialogState || moderationDialogState.loading) return;
  moderationDialogState.loading = true;
  renderModerationDialog();
  try {
    if (moderationDialogState.kind === 'report') {
      await api(`/api/admin/reports/${moderationDialogState.reportId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: moderationDialogState.status,
          resolutionNotes: moderationDialogState.notes,
        }),
      });
      toast('Report moderation updated.', 'success');
    } else {
      await api(`/api/admin/reviews/${moderationDialogState.reviewId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          visible: moderationDialogState.visible,
          moderationNotes: moderationDialogState.notes,
        }),
      });
      toast('Review moderation updated.', 'success');
    }
    moderationDialogState = null;
    closeModal();
    await loadAdminData(true);
  } catch (err) {
    moderationDialogState.loading = false;
    renderModerationDialog();
    toast(err.message || 'Could not save moderation action.', 'error');
  }
}

function paginateCollection(items, state) {
  const safeSize = Math.max(1, Number(state?.size || 1));
  const totalPages = Math.max(1, Math.ceil((items.length || 0) / safeSize) || 1);
  state.page = Math.min(Math.max(0, Number(state?.page || 0)), totalPages - 1);
  const start = state.page * safeSize;
  return {
    items: items.slice(start, start + safeSize),
    page: state.page,
    totalPages,
    total: items.length,
  };
}

function renderAdminPagination(section, page) {
  if (!page || page.totalPages <= 1) return '';
  return `
    <div class="pagination-row admin-pagination-row">
      <button class="btn btn-outline btn-sm" type="button" onclick="setAdminPage('${section}', -1)" ${page.page <= 0 ? 'disabled' : ''}>Previous</button>
      <span>Page ${page.page + 1} of ${page.totalPages}</span>
      <button class="btn btn-outline btn-sm" type="button" onclick="setAdminPage('${section}', 1)" ${page.page >= page.totalPages - 1 ? 'disabled' : ''}>Next</button>
    </div>
  `;
}

function setAdminPage(section, delta) {
  if (!adminState[section]) return;
  adminState[section].page = Math.max(0, Number(adminState[section].page || 0) + Number(delta || 0));
  if (section === 'users') renderAdminUsers();
  if (section === 'sessions') renderAdminSessions();
  if (section === 'reports' || section === 'reviews') renderAdminReports();
}

function setAdminFilter(section, key, value) {
  if (!adminState[section]) return;
  adminState[section][key] = value;
  adminState[section].page = 0;
  if (section === 'users') renderAdminUsers();
  if (section === 'sessions') renderAdminSessions();
  if (section === 'reports' || section === 'reviews') renderAdminReports();
}

function copyPublicProfileLink() {
  copyText(publicProfileUrl(currentUser.username || currentUser.id), 'Public profile link copied.');
}

async function copyText(text, successMessage = 'Copied.') {
  try {
    await navigator.clipboard.writeText(String(text || ''));
    toast(successMessage, 'success');
  } catch {
    toast('Copy failed on this device.', 'error');
  }
}

async function reportUser(reportedUserId, sessionId = '') {
  openReportUserDialog(reportedUserId, sessionId);
}

function openReportUserDialog(reportedUserId, sessionId = '') {
  reportDialogState = {
    reportedUserId,
    sessionId,
    category: REPORT_CATEGORY_OPTIONS[0].value,
    details: '',
    loading: false,
    submitted: null,
  };
  renderReportUserDialog();
}

function updateReportDialogField(field, value) {
  if (!reportDialogState) return;
  reportDialogState[field] = value;
  renderReportUserDialog();
}

function closeReportUserDialog() {
  reportDialogState = null;
  closeModal();
}

function renderReportUserDialog() {
  if (!reportDialogState) return;
  if (reportDialogState.submitted) {
    modal(`
      <div class="modal-head">
        <h2>Report submitted</h2>
        <p class="muted">Your report is now visible in the moderation queue with status <strong>OPEN</strong>.</p>
      </div>
      <div class="moderation-summary-list">
        <div><span>Category</span><strong>${esc(reportCategoryLabel(reportDialogState.submitted.reason))}</strong></div>
        <div><span>Report id</span><strong>${esc(reportDialogState.submitted.id || 'Generated')}</strong></div>
        <div><span>Created</span><strong>${esc(fmtDate(reportDialogState.submitted.createdAt))}</strong></div>
      </div>
      <div class="modal-actions">
        <button class="btn btn-primary" type="button" onclick="closeReportUserDialog()">Done</button>
      </div>
    `);
    return;
  }
  const selected = REPORT_CATEGORY_OPTIONS.find(item => item.value === reportDialogState.category) || REPORT_CATEGORY_OPTIONS[0];
  modal(`
    <div class="modal-head">
      <h2>Report user</h2>
      <p class="muted">Choose a trust category and include concise details for moderation review.</p>
    </div>
    <div class="moderation-form-grid">
      <label class="form-group">
        <span>Report category</span>
        <select class="input" onchange="updateReportDialogField('category', this.value)">
          ${REPORT_CATEGORY_OPTIONS.map(option => `<option value="${esc(option.value)}" ${reportDialogState.category === option.value ? 'selected' : ''}>${esc(option.label)}</option>`).join('')}
        </select>
      </label>
      <p class="moderation-hint">${esc(selected.hint)}</p>
      <label class="form-group">
        <span>Details for moderation</span>
        <textarea placeholder="Include what happened, when, and any impact. Avoid sensitive personal data." oninput="updateReportDialogField('details', this.value)">${esc(reportDialogState.details || '')}</textarea>
      </label>
      <p class="moderation-hint">${reportDialogState.sessionId ? 'This report is linked to the selected session.' : 'This report is profile-level and not tied to a specific session.'}</p>
    </div>
    <div class="modal-actions">
      <button class="btn btn-outline" type="button" onclick="closeReportUserDialog()">Cancel</button>
      <button class="btn btn-primary" type="button" onclick="submitReportUserDialog()" ${reportDialogState.loading ? 'disabled' : ''}>${reportDialogState.loading ? 'Submitting...' : 'Submit report'}</button>
    </div>
  `);
}

async function submitReportUserDialog() {
  if (!reportDialogState || reportDialogState.loading) return;
  if (!reportDialogState.category) {
    toast('Select a report category.', 'error');
    return;
  }
  if (String(reportDialogState.details || '').trim().length < 12) {
    toast('Please share a little more detail (at least 12 characters).', 'error');
    return;
  }
  reportDialogState.loading = true;
  renderReportUserDialog();
  const selected = REPORT_CATEGORY_OPTIONS.find(item => item.value === reportDialogState.category) || REPORT_CATEGORY_OPTIONS[0];
  try {
    const created = await api('/api/trust/reports', {
      method: 'POST',
      body: JSON.stringify({
        reportedUserId: reportDialogState.reportedUserId,
        sessionId: reportDialogState.sessionId,
        category: selected.value,
        reason: selected.label,
        details: reportDialogState.details,
      }),
    });
    reportDialogState.loading = false;
    reportDialogState.submitted = created;
    renderReportUserDialog();
    toast('Report submitted for moderation.', 'success');
  } catch (err) {
    reportDialogState.loading = false;
    renderReportUserDialog();
    toast(err.message || 'Could not submit report.', 'error');
  }
}

async function submitVerificationRequest() {
  const linkedInUrl = val('profile-linkedin-url');
  const companyEmail = val('profile-company-email');
  const notes = val('profile-verification-notes');
  if (!linkedInUrl && !companyEmail && !notes) {
    toast('Add verification evidence before submitting.', 'error');
    return;
  }
  try {
    const updated = await api('/api/trust/verification-request', {
      method: 'POST',
      body: JSON.stringify({ linkedInUrl, companyEmail, notes }),
    });
    currentUser = updated;
    localStorage.setItem('ip_user', JSON.stringify(updated));
    initUi();
    renderProfile();
    toast('Verification request submitted for review.', 'success');
  } catch (err) {
    toast(err.message || 'Could not submit verification request.', 'error');
  }
}

function bindFeedbackForm() {
  document.getElementById('feedback-form').addEventListener('submit', async event => {
    event.preventDefault();
    const btn = document.getElementById('feedback-submit');
    btn.disabled = true;
    hideAlert('feedback-alert');
    try {
      const comments = val('fb-comments').trim();
      if (comments.length < 16 || comments.split(/\s+/).filter(Boolean).length < 3) {
        throw new Error('Reviews must be specific and meaningful before submission.');
      }
      const topicFeedback = collectTopicFeedback();
      await api('/api/feedback', {
        method: 'POST',
        body: JSON.stringify({
          sessionId: val('fb-session'),
          rating: Number(val('fb-rating')),
          communication: Number(val('fb-communication')),
          technicalSkills: Number(val('fb-technical')),
          comments,
          strengths: val('fb-strengths'),
          weaknesses: val('fb-weaknesses'),
          improvementAreas: val('fb-recommendations'),
          recommendations: val('fb-recommendations'),
          topicFeedback,
        }),
      });
      document.getElementById('feedback-form').reset();
      renderFeedbackTopicSections();
      toast('Feedback submitted. Suspicious reviews may be held for moderation.', 'success');
      await loadFeedback();
    } catch (err) {
      showAlert('feedback-alert', err.message);
    } finally {
      btn.disabled = false;
    }
  });
}

function buildAuditQuery() {
  const params = new URLSearchParams();
  params.set('page', String(adminState.audit.page || 0));
  params.set('size', String(adminState.audit.size || 8));
  if (adminState.audit.entityType) params.set('entityType', adminState.audit.entityType);
  if (adminState.audit.subjectUserId) params.set('subjectUserId', adminState.audit.subjectUserId);
  return params.toString();
}

async function loadAdminData(force = false) {
  if (!hasAdminRole()) return;
  if (!force && adminOverview && adminTrustDashboard && adminAuditLogPage && adminUsers.length && adminSessions.length) {
    renderAdminPanels();
    return;
  }
  adminLoading = true;
  renderAdminPanels();
  try {
    const [overview, users, sessionsData, reports, reviews, trustDashboard, auditLogPage] = await Promise.all([
      api('/api/admin/overview'),
      api('/api/admin/users'),
      api('/api/admin/sessions'),
      api('/api/admin/reports'),
      api('/api/admin/reviews'),
      api('/api/admin/trust-dashboard'),
      api(`/api/admin/audit-logs?${buildAuditQuery()}`),
    ]);
    adminOverview = overview;
    adminUsers = Array.isArray(users) ? users : [];
    adminSessions = Array.isArray(sessionsData) ? sessionsData : [];
    adminReports = Array.isArray(reports) ? reports : [];
    adminReviews = Array.isArray(reviews) ? reviews : [];
    adminTrustDashboard = trustDashboard || null;
    adminAuditLogPage = auditLogPage || null;
  } catch (err) {
    toast(err.message || 'Could not load admin data.', 'error');
  } finally {
    adminLoading = false;
    renderAdminPanels();
  }
}

function renderAdminPanels() {
  if (!hasAdminRole()) return;
  const metricsHost = document.getElementById('admin-metrics-grid');
  const analyticsHost = document.getElementById('admin-analytics-panel');
  const liveHost = document.getElementById('admin-live-sessions-panel');
  const moderationSummary = adminReports.reduce((acc, item) => {
    const status = String(item?.status || 'OPEN').toUpperCase();
    if (status === 'ACTIONED') acc.actioned += 1;
    else if (status === 'REVIEWED') acc.reviewed += 1;
    else acc.open += 1;
    return acc;
  }, { open: 0, reviewed: 0, actioned: 0 });
  const trust = adminTrustDashboard || {};
  if (metricsHost) {
    metricsHost.innerHTML = [
      statMetric('Users', adminOverview?.totalUsers || 0, 'badge-purple'),
      statMetric('Flagged reviews', trust.flaggedReviewCount || adminOverview?.flaggedReviews || 0, 'badge-yellow'),
      statMetric('Pending verification', trust.pendingVerificationCount || adminOverview?.pendingVerificationRequests || 0, 'badge-yellow'),
      statMetric('Open reports', moderationSummary.open, 'badge-red'),
      statMetric('Avg trust score', `${trust.averageTrustScore || adminOverview?.averageTrustScore || 0}%`, 'badge-green'),
      statMetric('Avg review quality', `${trust.averageReviewQualityScore || adminOverview?.averageReviewQualityScore || 0}%`, 'badge-green'),
    ].join('');
  }
  if (analyticsHost) {
    const flaggedUsers = Array.isArray(trust.flaggedUsers) ? trust.flaggedUsers.slice(0, 4) : [];
    analyticsHost.innerHTML = adminLoading && !adminOverview
      ? skeletonCards(2)
      : `
        <div class="admin-analytics-stack">
          <section class="admin-analytics-section">
            <h3>Trust foundation metrics</h3>
            <div class="admin-analytics-copy">
              <div class="admin-analytics-metric"><span>Completion rate</span><strong>${esc(String(adminOverview?.completionRate || 0))}%</strong></div>
              <div class="admin-analytics-metric"><span>Cancellation rate</span><strong>${esc(String(adminOverview?.cancellationRate || 0))}%</strong></div>
              <div class="admin-analytics-metric"><span>Platform rating</span><strong>${esc(String(adminOverview?.platformAverageRating || 0))}</strong></div>
              <div class="admin-analytics-metric"><span>Queue pressure</span><strong>${esc(moderationSummary.open > 8 ? 'High' : moderationSummary.open > 3 ? 'Medium' : 'Low')}</strong></div>
            </div>
          </section>
          <section class="admin-analytics-section">
            <h3>Flagged users</h3>
            <div class="admin-list">
              ${flaggedUsers.map(item => `
                <article class="admin-list-item">
                  <div>
                    <strong>${esc(item.displayName || 'User')}</strong>
                    <p>${esc(item.email || '')}</p>
                    <div class="admin-signal-row">${renderSignalChips(item.indicators || [])}</div>
                  </div>
                  <span class="badge badge-${Number(item.trustScore || 0) < 70 ? 'red' : 'yellow'}">${esc(String(item.trustScore || 0))}%</span>
                </article>
              `).join('') || emptyState('No elevated user risk signals right now.')}
            </div>
          </section>
        </div>
      `;
  }
  if (liveHost) {
    liveHost.innerHTML = adminLoading && !adminSessions.length
      ? skeletonCards(2)
      : `
        <div class="panel-head"><h2>Upcoming session monitoring</h2><span class="badge badge-gray">${esc(String(adminSessions.length || 0))} total</span></div>
        <div class="admin-list">
          ${adminSessions.slice(0, 6).map(session => `
            <article class="admin-list-item">
              <div>
                <strong>${esc(sessionTitle(session))}</strong>
                <p>${esc(fmtDate(session.startTime))}</p>
                <small>${esc(providerLabel(session.meetingProvider))}</small>
              </div>
              <span class="badge badge-${statusClass((session.status || '').toUpperCase())}">${esc(session.status || 'PENDING')}</span>
            </article>
          `).join('') || emptyState('No sessions to monitor right now.')}
        </div>
      `;
  }
  renderAdminUsers();
  renderAdminSessions();
  renderAdminReports();
}

function renderAdminUsers() {
  const host = document.getElementById('admin-users-panel');
  if (!host) return;
  const query = String(document.getElementById('admin-user-search')?.value || '').trim().toLowerCase();
  const role = String(document.getElementById('admin-user-role')?.value || '').trim().toUpperCase();
  const enabled = String(document.getElementById('admin-user-enabled')?.value || '').trim().toLowerCase();
  const flaggedMap = new Map((adminTrustDashboard?.flaggedUsers || []).map(item => [item.userId, item]));
  if (adminLoading && !adminUsers.length) {
    host.innerHTML = skeletonCards(2);
    return;
  }
  const rows = adminUsers.filter(user => {
    const roleMatch = !role || (Array.isArray(user.roles) ? user.roles : [user.role]).includes(role);
    const searchMatch = !query || [user.username, user.displayName, user.email, user.company].some(value => String(value || '').toLowerCase().includes(query));
    const enabledMatch = !enabled || (enabled === 'enabled' ? user.accountEnabled !== false : user.accountEnabled === false);
    return roleMatch && enabledMatch && searchMatch;
  });
  const page = paginateCollection(rows, adminState.users);
  host.innerHTML = `
    <div class="admin-table-shell">
      <div class="admin-table-meta"><strong>${esc(String(rows.length))}</strong><span>matching users</span></div>
      <div class="admin-table">
      ${page.items.map(user => {
        const trust = flaggedMap.get(user.id);
        return `
          <article class="admin-table-row">
            <div class="admin-row-main">
              <div class="admin-row-head">
                <strong>${esc(accountDisplayName(user))}</strong>
                <div class="tag-row">
                  ${(user.roles || [user.role]).map(roleName => `<span>${esc(roleName)}</span>`).join('')}
                  ${user.interviewerVerified ? '<span>Verified interviewer</span>' : ''}
                  ${user.verificationRequestStatus && user.verificationRequestStatus !== 'NONE' ? `<span>${esc(user.verificationRequestStatus)}</span>` : ''}
                </div>
              </div>
              <p>${esc(user.email || '')}</p>
              ${trust ? `
                <div class="admin-user-metrics">
                  <span>Trust ${esc(String(trust.trustScore || 0))}%</span>
                  <span>Completion ${esc(String(trust.sessionCompletionRate || 0))}%</span>
                  <span>Cancellation ${esc(String(trust.cancellationReliability || 0))}%</span>
                  <span>Review quality ${esc(String(trust.reviewQualityScore || 0))}%</span>
                </div>
                <div class="admin-signal-row">${renderSignalChips(trust.indicators || [])}</div>
              ` : '<small>No elevated trust indicators.</small>'}
            </div>
            <div class="card-actions">
              ${user.roles?.includes?.('INTERVIEWER') ? `<button class="btn btn-outline btn-sm" type="button" onclick="toggleInterviewerVerification('${user.id}', '${user.interviewerVerified ? 'REJECTED' : user.verificationRequestStatus === 'PENDING' ? 'APPROVED' : 'PENDING'}')">${user.interviewerVerified ? 'Review verification' : 'Manage verification'}</button>` : ''}
              <button class="btn btn-outline btn-sm" type="button" onclick="togglePublicProfileVisibility('${user.id}', ${user.publicProfileVisible === false ? 'true' : 'false'})">${user.publicProfileVisible === false ? 'Show profile' : 'Hide profile'}</button>
              <button class="btn ${user.accountEnabled === false ? 'btn-success' : 'btn-danger'} btn-sm" type="button" onclick="toggleUserAccess('${user.id}', ${user.accountEnabled === false ? 'true' : 'false'})">${user.accountEnabled === false ? 'Reactivate' : 'Deactivate'}</button>
            </div>
          </article>
        `;
      }).join('') || emptyState('No users match the current filters.')}
      </div>
      ${renderAdminPagination('users', page)}
    </div>
  `;
}

function renderAdminReports() {
  const host = document.getElementById('admin-reports-panel');
  if (!host) return;
  if (adminLoading && !adminReports.length && !adminReviews.length) {
    host.innerHTML = skeletonCards(3);
    return;
  }
  const reportQuery = String(adminState.reports.query || '').trim().toLowerCase();
  const reportStatus = String(adminState.reports.status || '').trim().toUpperCase();
  const reportCategory = String(adminState.reports.category || '').trim().toUpperCase();
  const filteredReports = adminReports.filter(report => {
    const statusMatch = !reportStatus || String(report.status || '').toUpperCase() === reportStatus;
    const categoryValue = normalizeReportCategory(report.category || report.reason);
    const categoryMatch = !reportCategory || categoryValue === reportCategory;
    const searchText = [report.reason, report.details, report.reporterId, report.reportedUserId, report.reviewedByAdminId].filter(Boolean).join(' ').toLowerCase();
    return statusMatch && categoryMatch && (!reportQuery || searchText.includes(reportQuery));
  });
  const reportPage = paginateCollection(filteredReports, adminState.reports);
  const reviewQuery = String(adminState.reviews.query || '').trim().toLowerCase();
  const visibility = String(adminState.reviews.visibility || '').trim().toLowerCase();
  const minRating = Number(adminState.reviews.minRating || 0);
  const filteredReviews = adminReviews.filter(review => {
    const visibilityMatch = !visibility || (visibility === 'visible' ? review.publicReview === true : review.publicReview !== true);
    const ratingMatch = !minRating || Number(review.rating || 0) >= minRating;
    const topicText = Array.isArray(review.topicSummaries) ? review.topicSummaries.map(item => item.topic).join(' ') : '';
    const signalText = Array.isArray(review.suspiciousFlags) ? review.suspiciousFlags.join(' ') : '';
    const searchText = [review.reviewerName, review.interviewerName, review.comments, review.sessionTitle, topicText, review.moderationNotes, signalText].filter(Boolean).join(' ').toLowerCase();
    return visibilityMatch && ratingMatch && (!reviewQuery || searchText.includes(reviewQuery));
  });
  const reviewPage = paginateCollection(filteredReviews, adminState.reviews);
  const verificationQueue = adminTrustDashboard?.verificationQueue || [];
  const auditItems = adminAuditLogPage?.items || [];
  host.innerHTML = `
    <div class="admin-trust-summary-grid">
      <article class="admin-trust-card">
        <strong>Flagged reviews</strong>
        <span>${esc(String(adminTrustDashboard?.flaggedReviewCount || 0))}</span>
        <small>Spam patterns, bursts, or repeated low-quality submissions.</small>
      </article>
      <article class="admin-trust-card">
        <strong>Pending verification</strong>
        <span>${esc(String(adminTrustDashboard?.pendingVerificationCount || 0))}</span>
        <small>Interviewers waiting for approval or rejection.</small>
      </article>
      <article class="admin-trust-card">
        <strong>Flagged users</strong>
        <span>${esc(String(adminTrustDashboard?.flaggedUserCount || 0))}</span>
        <small>Users with trust indicators below healthy thresholds.</small>
      </article>
      <article class="admin-trust-card">
        <strong>Average trust score</strong>
        <span>${esc(String(adminTrustDashboard?.averageTrustScore || 0))}%</span>
        <small>Composite of completion, cancellations, consistency, and review quality.</small>
      </article>
    </div>
    <div class="admin-moderation-grid">
      <article class="admin-moderation-card">
        <div class="panel-head"><h2>Trust reports</h2><span class="badge badge-gray">${esc(String(filteredReports.length))}</span></div>
        <div class="admin-inline-filters">
          <input class="input" value="${esc(adminState.reports.query || '')}" placeholder="Search reports" oninput="setAdminFilter('reports', 'query', this.value)" />
          <select class="input" onchange="setAdminFilter('reports', 'status', this.value)">
            <option value="">All statuses</option>
            ${['OPEN', 'REVIEWED', 'ACTIONED', 'DISMISSED', 'DUPLICATE'].map(status => `<option value="${status}" ${reportStatus === status ? 'selected' : ''}>${status}</option>`).join('')}
          </select>
          <select class="input" onchange="setAdminFilter('reports', 'category', this.value)">
            <option value="">All categories</option>
            ${REPORT_CATEGORY_OPTIONS.map(option => `<option value="${esc(option.value)}" ${reportCategory === option.value ? 'selected' : ''}>${esc(option.label)}</option>`).join('')}
          </select>
        </div>
        <div class="admin-table">
          ${reportPage.items.map(report => `
            <article class="admin-table-row">
              <div class="admin-row-main">
                <div class="admin-row-head">
                  <strong>Trust report</strong>
                  <div class="tag-row">
                    <span>${esc(reportCategoryLabel(report.category || report.reason))}</span>
                    ${report.duplicateCount ? `<span>${esc(String(report.duplicateCount))} related</span>` : ''}
                  </div>
                </div>
                <p>${esc(report.details || 'No details provided.')}</p>
                <small>Reporter ${esc(report.reporterId || 'unknown')} • Target ${esc(report.reportedUserId || 'unknown')}</small>
                <small>${esc(fmtDate(report.createdAt))}${report.reviewedByAdminId ? ` • Moderated by ${esc(report.reviewedByAdminId)}` : ''}</small>
                ${report.resolutionNotes ? `<p class="admin-note-chip">Last note: ${esc(report.resolutionNotes)}</p>` : ''}
              </div>
              <div class="card-actions">
                <span class="badge badge-${report.status === 'OPEN' ? 'yellow' : report.status === 'ACTIONED' ? 'red' : 'gray'}">${esc(report.status || 'OPEN')}</span>
                <button class="btn btn-outline btn-sm" type="button" onclick="openReportModerationModal('${report.id}', 'REVIEWED')">Review</button>
                <button class="btn btn-danger btn-sm" type="button" onclick="openReportModerationModal('${report.id}', 'ACTIONED')">Action</button>
              </div>
            </article>
          `).join('') || emptyState('No trust reports match the current filters.')}
        </div>
        ${renderAdminPagination('reports', reportPage)}
      </article>
      <article class="admin-moderation-card">
        <div class="panel-head"><h2>Public review moderation</h2><span class="badge badge-gray">${esc(String(filteredReviews.length))}</span></div>
        <div class="admin-inline-filters">
          <input class="input" value="${esc(adminState.reviews.query || '')}" placeholder="Search reviews" oninput="setAdminFilter('reviews', 'query', this.value)" />
          <select class="input" onchange="setAdminFilter('reviews', 'visibility', this.value)">
            <option value="">All visibility</option>
            <option value="visible" ${visibility === 'visible' ? 'selected' : ''}>Visible</option>
            <option value="hidden" ${visibility === 'hidden' ? 'selected' : ''}>Hidden</option>
          </select>
          <select class="input" onchange="setAdminFilter('reviews', 'minRating', this.value)">
            <option value="">Any rating</option>
            <option value="4" ${String(adminState.reviews.minRating || '') === '4' ? 'selected' : ''}>4+ rating</option>
            <option value="3" ${String(adminState.reviews.minRating || '') === '3' ? 'selected' : ''}>3+ rating</option>
            <option value="2" ${String(adminState.reviews.minRating || '') === '2' ? 'selected' : ''}>2+ rating</option>
          </select>
        </div>
        <div class="admin-table">
          ${reviewPage.items.map(review => `
            <article class="admin-table-row admin-review-row">
              <div class="admin-row-main">
                <div class="admin-row-head">
                  <strong>${esc(review.reviewerName || 'InterviewPrep member')} → ${esc(review.interviewerName || 'Interviewer')}</strong>
                  <div class="tag-row">
                    ${review.interviewerVerified ? '<span>Verified</span>' : '<span>Unverified</span>'}
                    ${review.flaggedForModeration ? '<span>Flagged</span>' : ''}
                  </div>
                </div>
                <p>${esc(review.comments || 'No written review provided.')}</p>
                <div class="admin-signal-row">
                  ${review.reviewQualityScore != null ? `<span class="admin-signal-chip">Quality ${esc(String(review.reviewQualityScore))}</span>` : ''}
                  ${review.suspiciousScore != null ? `<span class="admin-signal-chip">Risk ${esc(String(review.suspiciousScore))}</span>` : ''}
                  ${renderSignalChips(review.suspiciousFlags || [])}
                </div>
                <small>${esc(review.sessionTitle || 'Interview session')} • ${esc(fmtDate(review.createdAt))}</small>
                ${review.interviewerReliability != null ? `<small>Interviewer trust ${esc(String(review.interviewerReliability))}%${review.interviewerCancelledSessions != null ? ` • Cancelled ${esc(String(review.interviewerCancelledSessions))}` : ''}</small>` : ''}
                ${review.moderationNotes ? `<p class="admin-note-chip">Last note: ${esc(review.moderationNotes)}</p>` : ''}
              </div>
              <div class="card-actions">
                <span class="badge badge-${review.publicReview ? 'green' : 'gray'}">${review.publicReview ? 'Visible' : 'Hidden'}</span>
                <button class="btn btn-outline btn-sm" type="button" onclick="openReviewModerationModal('${review.id}', ${review.publicReview ? 'false' : 'true'})">${review.publicReview ? 'Hide' : 'Republish'}</button>
              </div>
            </article>
          `).join('') || emptyState('No review items match the current filters.')}
        </div>
        ${renderAdminPagination('reviews', reviewPage)}
      </article>
      <article class="admin-moderation-card">
        <div class="panel-head"><h2>Verification queue</h2><span class="badge badge-gray">${esc(String(verificationQueue.length))}</span></div>
        <div class="admin-table">
          ${verificationQueue.map(item => `
            <article class="admin-table-row">
              <div class="admin-row-main">
                <div class="admin-row-head">
                  <strong>${esc(item.displayName || 'Interviewer')}</strong>
                  <span class="badge badge-${item.status === 'REJECTED' ? 'red' : 'yellow'}">${esc(item.status || 'PENDING')}</span>
                </div>
                <p>${esc(item.email || '')}</p>
                ${item.linkedInUrl ? `<small><a href="${esc(item.linkedInUrl)}" target="_blank" rel="noreferrer">${esc(item.linkedInUrl)}</a></small>` : ''}
                ${item.companyEmail ? `<small>${esc(item.companyEmail)}</small>` : ''}
                ${item.requestNotes ? `<p class="admin-note-chip">Request: ${esc(item.requestNotes)}</p>` : ''}
                ${item.adminNotes ? `<p class="admin-note-chip">Admin note: ${esc(item.adminNotes)}</p>` : ''}
              </div>
              <div class="card-actions">
                <button class="btn btn-outline btn-sm" type="button" onclick="toggleInterviewerVerification('${item.userId}', 'APPROVED')">Approve</button>
                <button class="btn btn-danger btn-sm" type="button" onclick="toggleInterviewerVerification('${item.userId}', 'REJECTED')">Reject</button>
              </div>
            </article>
          `).join('') || emptyState('No verification requests are waiting right now.')}
        </div>
      </article>
      <article class="admin-moderation-card">
        <div class="panel-head"><h2>Moderation history</h2><span class="badge badge-gray">${esc(String(adminAuditLogPage?.totalElements || auditItems.length || 0))}</span></div>
        <div class="admin-inline-filters">
          <select class="input" onchange="setAuditFilter('entityType', this.value)">
            <option value="">All entities</option>
            ${['USER', 'REVIEW', 'REPORT', 'VERIFICATION'].map(type => `<option value="${type}" ${String(adminState.audit.entityType || '') === type ? 'selected' : ''}>${type}</option>`).join('')}
          </select>
        </div>
        <div class="audit-log-list">
          ${auditItems.map(item => `
            <article class="audit-log-item">
              <div class="admin-row-head">
                <strong>${esc(item.action || item.entityType || 'ACTION')}</strong>
                <span class="badge badge-gray">${esc(item.entityType || 'AUDIT')}</span>
              </div>
              <p>${esc(item.summary || 'Moderation action recorded.')}</p>
              <small>${esc(fmtDate(item.createdAt))} • Actor ${esc(item.actorUserId || 'system')} • Subject ${esc(item.subjectUserId || item.entityId || 'unknown')}</small>
              ${item.reason ? `<p class="admin-note-chip">Reason: ${esc(item.reason)}</p>` : ''}
            </article>
          `).join('') || emptyState('No moderation history available yet.')}
        </div>
        ${renderAuditPagination()}
      </article>
    </div>
  `;
}

function statMetric(label, value, badgeClass) {
  return `<div class="stat-card"><span>${esc(String(value))}</span><p>${esc(label)}</p><small class="badge ${badgeClass}">${esc(label)}</small></div>`;
}

function renderSignalChips(signals) {
  return (signals || []).map(signal => `<span class="admin-signal-chip">${esc(String(signal).replaceAll('_', ' ').toLowerCase())}</span>`).join('');
}

function renderAuditPagination() {
  if (!adminAuditLogPage || Number(adminAuditLogPage.totalPages || 0) <= 1) return '';
  const page = Number(adminAuditLogPage.page || 0);
  const totalPages = Number(adminAuditLogPage.totalPages || 1);
  return `
    <div class="pagination-row admin-pagination-row">
      <button class="btn btn-outline btn-sm" type="button" onclick="changeAuditPage(-1)" ${page <= 0 ? 'disabled' : ''}>Previous</button>
      <span>Page ${page + 1} of ${totalPages}</span>
      <button class="btn btn-outline btn-sm" type="button" onclick="changeAuditPage(1)" ${page >= totalPages - 1 ? 'disabled' : ''}>Next</button>
    </div>
  `;
}

function setAuditFilter(key, value) {
  adminState.audit[key] = value;
  adminState.audit.page = 0;
  loadAdminData(true);
}

function changeAuditPage(delta) {
  adminState.audit.page = Math.max(0, Number(adminState.audit.page || 0) + Number(delta || 0));
  loadAdminData(true);
}

function toggleUserAccess(userId, enabled) {
  const user = adminUsers.find(item => item.id === userId);
  if (!user) return toast('User not found.', 'error');
  moderationDialogState = {
    kind: 'user',
    step: 'form',
    loading: false,
    userId,
    enabled,
    publicProfileVisible: null,
    title: enabled ? 'Reactivate user' : 'Deactivate user',
    notes: '',
    reason: '',
    confirmChecked: false,
  };
  renderModerationDialog();
}

function togglePublicProfileVisibility(userId, publicProfileVisible) {
  const user = adminUsers.find(item => item.id === userId);
  if (!user) return toast('User not found.', 'error');
  moderationDialogState = {
    kind: 'user',
    step: 'form',
    loading: false,
    userId,
    enabled: null,
    publicProfileVisible,
    title: publicProfileVisible ? 'Show public profile' : 'Hide public profile',
    notes: '',
    reason: '',
    confirmChecked: false,
  };
  renderModerationDialog();
}

function toggleInterviewerVerification(userId, suggestedStatus) {
  const user = adminUsers.find(item => item.id === userId) || (adminTrustDashboard?.verificationQueue || []).find(item => item.userId === userId);
  if (!user) return toast('Interviewer not found.', 'error');
  moderationDialogState = {
    kind: 'verification',
    step: 'form',
    loading: false,
    userId,
    status: suggestedStatus || (user.interviewerVerified ? 'REJECTED' : 'APPROVED'),
    notes: user.verificationNotes || user.adminNotes || '',
    reason: '',
    confirmChecked: false,
  };
  renderModerationDialog();
}

function openReportModerationModal(reportId, suggestedStatus = 'REVIEWED') {
  const report = adminReports.find(item => item.id === reportId);
  if (!report) {
    toast('Report not found in current queue.', 'error');
    return;
  }
  moderationDialogState = {
    kind: 'report',
    step: 'form',
    loading: false,
    reportId,
    status: String(suggestedStatus || report.status || 'REVIEWED').toUpperCase(),
    notes: report.resolutionNotes || '',
    reason: '',
    confirmChecked: false,
  };
  renderModerationDialog();
}

function openReviewModerationModal(reviewId, visible) {
  const review = adminReviews.find(item => item.id === reviewId);
  if (!review) {
    toast('Review not found in current queue.', 'error');
    return;
  }
  moderationDialogState = {
    kind: 'review',
    step: 'form',
    loading: false,
    reviewId,
    visible: visible !== false,
    notes: review.moderationNotes || '',
    reason: '',
    confirmChecked: false,
  };
  renderModerationDialog();
}

function continueModerationDialog() {
  if (!moderationDialogState) return;
  if (String(moderationDialogState.reason || '').trim().length < 6) {
    toast('Add a clear moderation reason.', 'error');
    return;
  }
  if (moderationDialogState.kind === 'report' && moderationDialogState.status === 'ACTIONED' && String(moderationDialogState.notes || '').trim().length < 8) {
    toast('Add clear action notes before proceeding.', 'error');
    return;
  }
  if (moderationDialogState.kind === 'review' && !moderationDialogState.visible && String(moderationDialogState.notes || '').trim().length < 8) {
    toast('Add moderation notes before hiding a review.', 'error');
    return;
  }
  if (moderationDialogState.kind === 'verification' && moderationDialogState.status === 'REJECTED' && String(moderationDialogState.notes || '').trim().length < 8) {
    toast('Add a rejection note for the interviewer.', 'error');
    return;
  }
  if (!moderationDialogState.confirmChecked) {
    toast('Confirm the moderation action before continuing.', 'error');
    return;
  }
  moderationDialogState.step = 'confirm';
  renderModerationDialog();
}

function renderModerationDialog() {
  if (!moderationDialogState) return;
  if (moderationDialogState.kind === 'user') {
    const user = adminUsers.find(item => item.id === moderationDialogState.userId);
    if (!user) return closeModerationDialog();
    const changeSummary = moderationDialogState.enabled != null
      ? `${moderationDialogState.enabled ? 'Enable access' : 'Disable access'} for ${accountDisplayName(user)}`
      : `${moderationDialogState.publicProfileVisible ? 'Show public profile' : 'Hide public profile'} for ${accountDisplayName(user)}`;
    if (moderationDialogState.step === 'confirm') {
      modal(`
        <div class="modal-head">
          <h2>Confirm user moderation</h2>
          <p class="muted">${esc(changeSummary)}</p>
        </div>
        <div class="moderation-summary-list">
          <div><span>User</span><strong>${esc(accountDisplayName(user))}</strong></div>
          <div><span>Reason</span><strong>${esc(moderationDialogState.reason)}</strong></div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" type="button" onclick="backModerationDialog()">Back</button>
          <button class="btn btn-primary" type="button" onclick="submitModerationDialog()" ${moderationDialogState.loading ? 'disabled' : ''}>${moderationDialogState.loading ? 'Saving...' : 'Confirm moderation'}</button>
        </div>
      `);
      return;
    }
    modal(`
      <div class="modal-head">
        <h2>${esc(moderationDialogState.title || 'User moderation')}</h2>
        <p class="muted">All admin actions are written to the moderation audit log.</p>
      </div>
      <div class="moderation-form-grid">
        <label class="form-group">
          <span>Moderation reason</span>
          <textarea placeholder="Document why this user access change is necessary." oninput="updateModerationDialogField('reason', this.value)">${esc(moderationDialogState.reason || '')}</textarea>
        </label>
        <label class="check-row">
          <input type="checkbox" ${moderationDialogState.confirmChecked ? 'checked' : ''} onchange="updateModerationDialogField('confirmChecked', this.checked)" />
          I confirm this action is necessary and accurate.
        </label>
      </div>
      <div class="modal-actions">
        <button class="btn btn-outline" type="button" onclick="closeModerationDialog()">Cancel</button>
        <button class="btn btn-primary" type="button" onclick="continueModerationDialog()">Continue</button>
      </div>
    `);
    return;
  }
  if (moderationDialogState.kind === 'verification') {
    const user = adminUsers.find(item => item.id === moderationDialogState.userId) || (adminTrustDashboard?.verificationQueue || []).find(item => item.userId === moderationDialogState.userId);
    if (!user) return closeModerationDialog();
    if (moderationDialogState.step === 'confirm') {
      modal(`
        <div class="modal-head">
          <h2>Confirm verification decision</h2>
          <p class="muted">This updates interviewer verification status immediately.</p>
        </div>
        <div class="moderation-summary-list">
          <div><span>Interviewer</span><strong>${esc(user.displayName || accountDisplayName(user))}</strong></div>
          <div><span>Status</span><strong>${esc(moderationDialogState.status)}</strong></div>
          <div><span>Reason</span><strong>${esc(moderationDialogState.reason)}</strong></div>
          <div><span>Notes</span><strong>${esc(moderationDialogState.notes || 'No note added')}</strong></div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" type="button" onclick="backModerationDialog()">Back</button>
          <button class="btn btn-primary" type="button" onclick="submitModerationDialog()" ${moderationDialogState.loading ? 'disabled' : ''}>${moderationDialogState.loading ? 'Saving...' : 'Confirm decision'}</button>
        </div>
      `);
      return;
    }
    modal(`
      <div class="modal-head">
        <h2>Manage interviewer verification</h2>
        <p class="muted">Approve, reject, or keep a request pending with an audit reason.</p>
      </div>
      <div class="moderation-form-grid">
        <label class="form-group">
          <span>Status</span>
          <select class="input" onchange="updateModerationDialogField('status', this.value)">
            ${['PENDING', 'APPROVED', 'REJECTED'].map(status => `<option value="${status}" ${moderationDialogState.status === status ? 'selected' : ''}>${status}</option>`).join('')}
          </select>
        </label>
        <label class="form-group">
          <span>Admin notes</span>
          <textarea placeholder="Explain evidence reviewed or next steps." oninput="updateModerationDialogField('notes', this.value)">${esc(moderationDialogState.notes || '')}</textarea>
        </label>
        <label class="form-group">
          <span>Audit reason</span>
          <textarea placeholder="Why are you making this verification decision?" oninput="updateModerationDialogField('reason', this.value)">${esc(moderationDialogState.reason || '')}</textarea>
        </label>
        <label class="check-row">
          <input type="checkbox" ${moderationDialogState.confirmChecked ? 'checked' : ''} onchange="updateModerationDialogField('confirmChecked', this.checked)" />
          I reviewed the interviewer evidence.
        </label>
      </div>
      <div class="modal-actions">
        <button class="btn btn-outline" type="button" onclick="closeModerationDialog()">Cancel</button>
        <button class="btn btn-primary" type="button" onclick="continueModerationDialog()">Continue</button>
      </div>
    `);
    return;
  }
  if (moderationDialogState.kind === 'report') {
    const report = adminReports.find(item => item.id === moderationDialogState.reportId);
    if (!report) return closeModerationDialog();
    if (moderationDialogState.step === 'confirm') {
      modal(`
        <div class="modal-head">
          <h2>Confirm report moderation</h2>
          <p class="muted">This decision will be saved to the trust queue immediately.</p>
        </div>
        <div class="moderation-summary-list">
          <div><span>Category</span><strong>${esc(reportCategoryLabel(report.category || report.reason))}</strong></div>
          <div><span>Decision</span><strong>${esc(moderationDialogState.status)}</strong></div>
          <div><span>Reason</span><strong>${esc(moderationDialogState.reason)}</strong></div>
          <div><span>Notes</span><strong>${esc(moderationDialogState.notes || 'No notes added')}</strong></div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" type="button" onclick="backModerationDialog()">Back</button>
          <button class="btn btn-primary" type="button" onclick="submitModerationDialog()" ${moderationDialogState.loading ? 'disabled' : ''}>${moderationDialogState.loading ? 'Saving...' : 'Confirm moderation'}</button>
        </div>
      `);
      return;
    }
    modal(`
      <div class="modal-head">
        <h2>Moderate trust report</h2>
        <p class="muted">${esc(reportCategoryHint(report.category || report.reason) || 'Review details, then choose a moderation outcome.')}</p>
      </div>
      <div class="moderation-form-grid">
        <label class="form-group">
          <span>Decision</span>
          <select class="input" onchange="updateModerationDialogField('status', this.value)">
            ${['OPEN', 'REVIEWED', 'ACTIONED', 'DISMISSED', 'DUPLICATE'].map(status => `<option value="${status}" ${moderationDialogState.status === status ? 'selected' : ''}>${status}</option>`).join('')}
          </select>
        </label>
        <label class="form-group">
          <span>Moderation notes</span>
          <textarea placeholder="Document evidence, policy alignment, and next steps." oninput="updateModerationDialogField('notes', this.value)">${esc(moderationDialogState.notes || '')}</textarea>
        </label>
        <label class="form-group">
          <span>Audit reason</span>
          <textarea placeholder="Why are you applying this report decision?" oninput="updateModerationDialogField('reason', this.value)">${esc(moderationDialogState.reason || '')}</textarea>
        </label>
        <label class="check-row">
          <input type="checkbox" ${moderationDialogState.confirmChecked ? 'checked' : ''} onchange="updateModerationDialogField('confirmChecked', this.checked)" />
          I confirm this report decision is accurate.
        </label>
      </div>
      <div class="modal-actions">
        <button class="btn btn-outline" type="button" onclick="closeModerationDialog()">Cancel</button>
        <button class="btn btn-primary" type="button" onclick="continueModerationDialog()">Continue</button>
      </div>
    `);
    return;
  }
  const review = adminReviews.find(item => item.id === moderationDialogState.reviewId);
  if (!review) return closeModerationDialog();
  if (moderationDialogState.step === 'confirm') {
    modal(`
      <div class="modal-head">
        <h2>Confirm review moderation</h2>
        <p class="muted">Visibility changes apply to public profile pages instantly.</p>
      </div>
      <div class="moderation-summary-list">
        <div><span>Reviewer</span><strong>${esc(review.reviewerName || 'InterviewPrep member')}</strong></div>
        <div><span>Visibility</span><strong>${moderationDialogState.visible ? 'Visible' : 'Hidden'}</strong></div>
        <div><span>Reason</span><strong>${esc(moderationDialogState.reason)}</strong></div>
        <div><span>Notes</span><strong>${esc(moderationDialogState.notes || 'No notes added')}</strong></div>
      </div>
      <div class="modal-actions">
        <button class="btn btn-outline" type="button" onclick="backModerationDialog()">Back</button>
        <button class="btn btn-primary" type="button" onclick="submitModerationDialog()" ${moderationDialogState.loading ? 'disabled' : ''}>${moderationDialogState.loading ? 'Saving...' : 'Confirm moderation'}</button>
      </div>
    `);
    return;
  }
  modal(`
    <div class="modal-head">
      <h2>Moderate public review</h2>
      <p class="muted">Capture context for auditability before changing visibility.</p>
    </div>
    <div class="moderation-form-grid">
      <label class="form-group">
        <span>Visibility decision</span>
        <select class="input" onchange="updateModerationDialogField('visible', this.value === 'true')">
          <option value="true" ${moderationDialogState.visible ? 'selected' : ''}>Visible</option>
          <option value="false" ${!moderationDialogState.visible ? 'selected' : ''}>Hidden</option>
        </select>
      </label>
      <label class="form-group">
        <span>Moderation notes</span>
        <textarea placeholder="Why is this review visible or hidden?" oninput="updateModerationDialogField('notes', this.value)">${esc(moderationDialogState.notes || '')}</textarea>
      </label>
      <label class="form-group">
        <span>Audit reason</span>
        <textarea placeholder="What integrity or abuse concern is this action addressing?" oninput="updateModerationDialogField('reason', this.value)">${esc(moderationDialogState.reason || '')}</textarea>
      </label>
      <label class="check-row">
        <input type="checkbox" ${moderationDialogState.confirmChecked ? 'checked' : ''} onchange="updateModerationDialogField('confirmChecked', this.checked)" />
        I confirm this visibility update follows trust policy.
      </label>
    </div>
    <div class="modal-actions">
      <button class="btn btn-outline" type="button" onclick="closeModerationDialog()">Cancel</button>
      <button class="btn btn-primary" type="button" onclick="continueModerationDialog()">Continue</button>
    </div>
  `);
}

async function submitModerationDialog() {
  if (!moderationDialogState || moderationDialogState.loading) return;
  moderationDialogState.loading = true;
  renderModerationDialog();
  try {
    if (moderationDialogState.kind === 'user') {
      await api(`/api/admin/users/${moderationDialogState.userId}/moderation`, {
        method: 'PATCH',
        body: JSON.stringify({
          enabled: moderationDialogState.enabled,
          publicProfileVisible: moderationDialogState.publicProfileVisible,
          reason: moderationDialogState.reason,
        }),
      });
      toast('User moderation updated.', 'success');
    } else if (moderationDialogState.kind === 'verification') {
      await api(`/api/admin/interviewers/${moderationDialogState.userId}/verify`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: moderationDialogState.status,
          notes: moderationDialogState.notes,
          reason: moderationDialogState.reason,
        }),
      });
      toast('Verification decision saved.', 'success');
    } else if (moderationDialogState.kind === 'report') {
      await api(`/api/admin/reports/${moderationDialogState.reportId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: moderationDialogState.status,
          resolutionNotes: moderationDialogState.notes,
          reason: moderationDialogState.reason,
        }),
      });
      toast('Report moderation updated.', 'success');
    } else {
      await api(`/api/admin/reviews/${moderationDialogState.reviewId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          visible: moderationDialogState.visible,
          moderationNotes: moderationDialogState.notes,
          reason: moderationDialogState.reason,
        }),
      });
      toast('Review moderation updated.', 'success');
    }
    moderationDialogState = null;
    closeModal();
    await loadAdminData(true);
  } catch (err) {
    moderationDialogState.loading = false;
    renderModerationDialog();
    toast(err.message || 'Could not save moderation action.', 'error');
  }
}

function viewerTimezone() {
  return currentUser?.timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone || '';
}

function esc(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

function jsArg(value) {
  return esc(JSON.stringify(String(value)));
}
