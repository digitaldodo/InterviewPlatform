const marketplaceState = {
  page: 0,
  totalPages: 1,
  totalItems: 0,
  debounceTimer: null,
  summary: null,
  filterOptions: { expertise: [], languages: [], companies: [], timeZones: [], topics: [], sessionDurations: [] },
};
const COMMON_TIMEZONES = ['Asia/Kolkata', 'UTC', 'Europe/London', 'America/New_York', 'America/Los_Angeles', 'Asia/Singapore'];

window.addEventListener('DOMContentLoaded', initPublicMarketplace);

async function initPublicMarketplace() {
  setCanonicalUrl(publicPageUrl('/marketplace.html'));
  hydrateMarketplaceSeo();
  initMarketplaceFilters();
  applyMarketplaceQueryParams();
  bindMarketplaceFilters();
  renderMarketplaceShortcuts();
  renderMarketplaceSkeletons();

  await Promise.all([loadMarketplaceSummary(), loadMarketplaceFilterOptions()]);
  await loadMarketplaceResults();
}

function initMarketplaceFilters() {
  FormUx.initMultiSearchSelect('filter-expertise', {
    placeholder: 'Expertise',
    label: 'Filter by expertise',
    className: 'discovery-filter-control',
  });
  FormUx.initSearchSelect('filter-company', {
    placeholder: 'Company',
    label: 'Filter by company',
    className: 'discovery-filter-control',
  });
  FormUx.initMultiSearchSelect('filter-language', {
    placeholder: 'Language',
    label: 'Filter by language',
    className: 'discovery-filter-control',
  });
  FormUx.initSearchSelect('filter-timezone', {
    placeholder: 'Timezone',
    label: 'Filter by timezone',
    className: 'discovery-filter-control',
  });
  FormUx.initMultiSearchSelect('filter-topic', {
    placeholder: 'Interview topic',
    label: 'Filter by topic',
    className: 'discovery-filter-control',
  });
}

function bindMarketplaceFilters() {
  [
    'marketplace-search',
    'marketplace-sort',
    'filter-years',
    'filter-rating',
    'filter-duration',
    'filter-available',
    'filter-available-today',
    'filter-verified',
    'filter-expertise',
    'filter-company',
    'filter-language',
    'filter-timezone',
    'filter-topic',
  ].forEach(id => {
    document.getElementById(id)?.addEventListener('input', scheduleMarketplaceRefresh);
    document.getElementById(id)?.addEventListener('change', scheduleMarketplaceRefresh);
  });
}

function scheduleMarketplaceRefresh() {
  clearTimeout(marketplaceState.debounceTimer);
  marketplaceState.debounceTimer = setTimeout(() => {
    marketplaceState.page = 0;
    loadMarketplaceResults();
  }, 220);
}

async function loadMarketplaceSummary() {
  try {
    const page = await publicApi('/api/interviewers/search?sort=top-rated&page=0&size=12&publicOnly=true');
    const visible = visiblePublicProfiles(page.items || []);
    marketplaceState.summary = {
      interviewerCount: Number(page.totalItems || visible.length || 0),
      verifiedCount: visible.filter(item => item.interviewerVerified).length,
      availableCount: visible.filter(item => item.acceptingBookings !== false).length,
      reviewCount: visible.reduce((sum, item) => sum + Number(item.reviewCount || 0), 0),
      availableTodayCount: visible.filter(item => item.acceptingBookings !== false).length,
      featuredInterviewers: visible.slice(0, 4),
    };
    const summary = marketplaceState.summary;
    const header = document.getElementById('marketplace-hero-note');
    if (header) {
      header.textContent = `${formatCount(summary.availableTodayCount)} interviewers have live availability today across ${formatCount(summary.interviewerCount)} public profiles.`;
    }
    document.getElementById('marketplace-stat-public').textContent = formatCount(summary.interviewerCount);
    document.getElementById('marketplace-stat-verified').textContent = formatCount(summary.verifiedCount);
    document.getElementById('marketplace-stat-available').textContent = formatCount(summary.availableCount);
    document.getElementById('marketplace-stat-reviews').textContent = formatCount(summary.reviewCount);
    const rail = document.getElementById('marketplace-hero-preview');
    if (rail) {
      rail.innerHTML = (summary.featuredInterviewers || []).slice(0, 3).map(renderMarketplaceHeroCard).join('');
    }
    hydrateMarketplaceSeo(summary);
  } catch (err) {
    const note = document.getElementById('marketplace-hero-note');
    if (note) note.textContent = err.message || 'Marketplace summary is temporarily unavailable.';
  }
}

async function loadMarketplaceFilterOptions() {
  try {
    const data = await publicApi('/api/interviewers/filter-options?publicOnly=true');
    marketplaceState.filterOptions = {
      expertise: Array.isArray(data?.expertise) ? data.expertise : [],
      languages: Array.isArray(data?.languages) ? data.languages : [],
      companies: Array.isArray(data?.companies) ? data.companies : [],
      timeZones: Array.isArray(data?.timeZones) ? data.timeZones : [],
      topics: Array.isArray(data?.topics) ? data.topics : [],
      sessionDurations: Array.isArray(data?.sessionDurations) ? data.sessionDurations : [],
    };
    document.getElementById('filter-expertise')?.__multiSearchSelectControl?.setOptions(marketplaceState.filterOptions.expertise);
    document.getElementById('filter-company')?.__searchSelectControl?.setOptions(marketplaceState.filterOptions.companies);
    document.getElementById('filter-language')?.__multiSearchSelectControl?.setOptions(marketplaceState.filterOptions.languages);
    document.getElementById('filter-timezone')?.__searchSelectControl?.setOptions(prioritizeCommonTimezones(marketplaceState.filterOptions.timeZones));
    document.getElementById('filter-topic')?.__multiSearchSelectControl?.setOptions(marketplaceState.filterOptions.topics);
    populateSelectOptions('filter-duration', marketplaceState.filterOptions.sessionDurations, 'Any duration', value => `${value} min`);
    renderMarketplaceShortcuts();
  } catch {}
}

function prioritizeCommonTimezones(values) {
  const items = uniqueFilterValues(values);
  const common = COMMON_TIMEZONES.filter(zone => items.some(item => item.toLowerCase() === zone.toLowerCase()));
  const rest = items.filter(item => !common.some(zone => zone.toLowerCase() === item.toLowerCase()));
  return [...common, ...rest];
}

function uniqueFilterValues(values) {
  const seen = new Set();
  return (Array.isArray(values) ? values : [])
    .map(value => String(value || '').replace(/\s+/g, ' ').trim())
    .filter(value => {
      const key = value.toLowerCase();
      if (!value || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

function populateSelectOptions(id, values, defaultLabel, renderLabel = value => value) {
  const select = document.getElementById(id);
  if (!select) return;
  const current = select.value;
  const items = (Array.isArray(values) ? values : []).filter(value => value != null && value !== '');
  select.innerHTML = `<option value="">${esc(defaultLabel)}</option>${items.map(value => `<option value="${esc(value)}">${esc(renderLabel(value))}</option>`).join('')}`;
  select.value = items.includes(Number(current)) || items.includes(current) ? current : '';
}

async function loadMarketplaceResults() {
  const grid = document.getElementById('public-marketplace-grid');
  if (!grid) return;
  renderMarketplaceSkeletons();
  const params = new URLSearchParams({
    q: controlValue('marketplace-search'),
    sort: controlValue('marketplace-sort') || 'top-rated',
    expertise: controlValue('filter-expertise'),
    company: controlValue('filter-company'),
    language: controlValue('filter-language'),
    timezone: controlValue('filter-timezone'),
    topic: controlValue('filter-topic'),
    publicOnly: 'true',
    page: String(marketplaceState.page),
    size: '9',
  });
  if (controlValue('filter-years')) params.set('minExperience', controlValue('filter-years'));
  if (controlValue('filter-rating')) params.set('minRating', controlValue('filter-rating'));
  if (controlValue('filter-duration')) params.set('sessionDuration', controlValue('filter-duration'));
  if (document.getElementById('filter-available').checked) params.set('available', 'true');
  if (document.getElementById('filter-available-today').checked) params.set('availableToday', 'true');
  if (document.getElementById('filter-verified').checked) params.set('verified', 'true');

  syncMarketplaceUrl();
  renderMarketplaceChips();
  renderMarketplaceShortcuts();

  try {
    const page = await publicApi(`/api/interviewers/search?${params.toString()}`);
    marketplaceState.totalPages = Math.max(1, page.totalPages || 1);
    marketplaceState.totalItems = Number(page.totalItems || 0);
    renderMarketplaceGrid(page.items || []);
    updateMarketplaceResultCopy();
    hydrateMarketplaceSeo(marketplaceState.summary, page.items || []);
  } catch (err) {
    grid.innerHTML = `<div class="empty-state empty-state-rich"><strong>Marketplace unavailable</strong><p>${esc(err.message || 'Could not load interviewer profiles right now.')}</p></div>`;
    marketplaceState.totalItems = 0;
    updateMarketplaceResultCopy();
  }
}

function renderMarketplaceGrid(items) {
  const grid = document.getElementById('public-marketplace-grid');
  const list = visiblePublicProfiles(items);
  if (!list.length) {
    grid.innerHTML = `
      <div class="empty-state empty-state-rich marketplace-empty">
        <strong>No interviewers match these filters</strong>
        <p>Try broadening the search, removing a topic filter, or focusing on verified profiles with live availability.</p>
        <a class="btn btn-outline btn-sm" href="${esc(marketplaceUrl())}">Reset marketplace</a>
      </div>
    `;
    return;
  }
  grid.classList.remove('skeleton-grid');
  grid.innerHTML = list.map(renderMarketplaceCard).join('');
}

function renderMarketplaceCard(interviewer) {
  const topics = Array.isArray(interviewer.interviewTopics) ? interviewer.interviewTopics.slice(0, 3) : [];
  return `
    <article class="interviewer-card marketplace-card">
      <div class="interviewer-card-main">
        <div class="interviewer-avatar-shell">
          ${avatarMarkup(interviewer, 'avatar avatar-profile')}
        </div>
        <div class="interviewer-card-copy">
          <div class="interviewer-card-title-row">
            <div class="interviewer-card-title">
              <h3>${esc(interviewer.displayName || interviewer.username || 'Interviewer')}</h3>
              <p>${esc(interviewer.currentRole || 'Interview coach')}</p>
              <span>${esc(interviewer.company || 'InterviewPrep marketplace')}</span>
            </div>
            <div class="marketplace-card-score">
              <strong>${esc(ratingLabel(interviewer.averageRating))}</strong>
              <small>${formatCount(interviewer.reviewCount)} reviews</small>
            </div>
          </div>
          <div class="marketplace-card-signals">
            <span>${formatCount(interviewer.completedInterviews)} sessions</span>
            <span>${esc(String(Number(interviewer.reliabilityScore || 0).toFixed(1)))}% reliability</span>
            <span>${esc(interviewer.timeZone || 'Timezone flexible')}</span>
            ${interviewer.interviewerVerified ? '<span class="badge badge-green">Verified</span>' : '<span class="badge badge-gray">Public</span>'}
          </div>
          <div class="tag-row">${topicTags(interviewer.skills, 4)}</div>
          ${topics.length ? `<div class="tag-row subtle">${topicTags(topics, 3)}</div>` : ''}
        </div>
      </div>
      <p class="bio interviewer-bio">${esc(bioPreview(interviewer.bio, 156))}</p>
      <div class="interviewer-card-footer">
        <span class="availability-pill ${interviewer.acceptingBookings === false ? 'is-muted' : ''}">${esc(availabilityLabel(interviewer))}</span>
        <span class="availability-pill">${esc(formatDurationList(interviewer.sessionDurations))}</span>
        <div class="card-actions">
          <a class="btn btn-outline btn-sm" href="${esc(profileUrl(interviewer.username))}">Public profile</a>
          <a class="btn btn-primary btn-sm" href="${esc(authUrl())}">Sign in to book</a>
        </div>
      </div>
    </article>
  `;
}

function renderMarketplaceHeroCard(interviewer) {
  return `
    <a class="preview-person-card" href="${esc(profileUrl(interviewer.username))}">
      ${avatarMarkup(interviewer, 'avatar avatar-compact')}
      <div>
        <strong>${esc(interviewer.displayName || interviewer.username || 'Interviewer')}</strong>
        <p>${esc(interviewer.currentRole || 'Interview coach')}</p>
      </div>
    </a>
  `;
}

function renderMarketplaceShortcuts() {
  const shortcuts = document.getElementById('marketplace-shortcuts');
  if (!shortcuts) return;
  const selected = controlValues('filter-topic').map(value => value.toLowerCase());
  const topics = marketplaceState.filterOptions.topics.slice(0, 8);
  shortcuts.innerHTML = topics.map(topic => {
    const active = selected.includes(String(topic).toLowerCase());
    return `<button class="chip ${active ? 'active' : ''}" type="button" aria-pressed="${active}" onclick="applyMarketplaceTopic('${esc(topic)}')">${esc(topic)}${active ? ' ×' : ''}</button>`;
  }).join('');
}

function applyMarketplaceTopic(topic) {
  document.getElementById('filter-topic')?.__multiSearchSelectControl?.toggleValue(topic);
  marketplaceState.page = 0;
  loadMarketplaceResults();
}

function renderMarketplaceChips() {
  const host = document.getElementById('marketplace-active-filters');
  if (!host) return;
  const chips = [];
  addChip(chips, 'marketplace-search', 'Search');
  addChip(chips, 'filter-expertise', 'Expertise');
  addChip(chips, 'filter-company', 'Company');
  addChip(chips, 'filter-language', 'Language');
  addChip(chips, 'filter-timezone', 'Timezone');
  addChip(chips, 'filter-topic', 'Topic');
  addChip(chips, 'filter-years', 'Experience');
  addChip(chips, 'filter-rating', 'Rating');
  addChip(chips, 'filter-duration', 'Duration');
  if (document.getElementById('filter-available').checked) chips.push(renderChip('Available', () => clearCheckbox('filter-available')));
  if (document.getElementById('filter-available-today').checked) chips.push(renderChip('Available today', () => clearCheckbox('filter-available-today')));
  if (document.getElementById('filter-verified').checked) chips.push(renderChip('Verified', () => clearCheckbox('filter-verified')));
  host.innerHTML = chips.join('') || '<span class="filter-match-note">All public interviewers</span>';
}

function addChip(chips, id, label) {
  const values = controlValues(id);
  values.forEach(value => chips.push(renderChip(`${label}: ${value}`, () => clearControl(id, value))));
}

function renderChip(label, action) {
  const id = `chip-${Math.random().toString(36).slice(2)}`;
  window[id] = () => {
    action();
    marketplaceState.page = 0;
    loadMarketplaceResults();
    delete window[id];
  };
  return `<button class="chip active" type="button" onclick="${id}()">${esc(label)} ×</button>`;
}

function clearControl(id, value = null) {
  const control = document.getElementById(id);
  if (!control) return;
  if (control.__multiSearchSelectControl) {
    if (value) {
      control.__multiSearchSelectControl.removeValue(value);
    } else {
      control.__multiSearchSelectControl.setValues([]);
    }
    return;
  }
  if (control.__searchSelectControl) {
    control.__searchSelectControl.setValue('');
    return;
  }
  control.value = '';
}

function clearCheckbox(id) {
  const control = document.getElementById(id);
  if (control) control.checked = false;
}

function clearMarketplaceFilters() {
  [
    'marketplace-search',
    'filter-years',
    'filter-rating',
    'filter-duration',
  ].forEach(clearControl);
  ['filter-expertise', 'filter-company', 'filter-language', 'filter-timezone', 'filter-topic'].forEach(clearControl);
  ['filter-available', 'filter-available-today', 'filter-verified'].forEach(clearCheckbox);
  document.getElementById('marketplace-sort').value = 'top-rated';
  marketplaceState.page = 0;
  loadMarketplaceResults();
}

function changeMarketplacePage(delta) {
  marketplaceState.page = Math.min(Math.max(0, marketplaceState.page + delta), marketplaceState.totalPages - 1);
  loadMarketplaceResults();
}

function updateMarketplaceResultCopy() {
  document.getElementById('marketplace-result-summary').textContent = marketplaceState.totalItems
    ? `${formatCount(marketplaceState.totalItems)} public interviewers found`
    : 'No public interviewers found';
  document.getElementById('marketplace-page-label').textContent = `Page ${marketplaceState.page + 1} of ${marketplaceState.totalPages}`;
  const bottom = document.getElementById('marketplace-page-label-bottom');
  if (bottom) bottom.textContent = `${formatCount(marketplaceState.totalItems)} results`;
}

function renderMarketplaceSkeletons() {
  const grid = document.getElementById('public-marketplace-grid');
  if (!grid) return;
  grid.classList.add('skeleton-grid');
  grid.innerHTML = skeletonCards(6, 'marketplace-skeleton');
}

function syncMarketplaceUrl() {
  const params = new URLSearchParams();
  setUrlParam(params, 'q', controlValue('marketplace-search'));
  setUrlParam(params, 'expertise', controlValue('filter-expertise'));
  setUrlParam(params, 'company', controlValue('filter-company'));
  setUrlParam(params, 'language', controlValue('filter-language'));
  setUrlParam(params, 'timezone', controlValue('filter-timezone'));
  setUrlParam(params, 'topic', controlValue('filter-topic'));
  setUrlParam(params, 'minExperience', controlValue('filter-years'));
  setUrlParam(params, 'minRating', controlValue('filter-rating'));
  setUrlParam(params, 'sessionDuration', controlValue('filter-duration'));
  setUrlParam(params, 'sort', controlValue('marketplace-sort'));
  if (document.getElementById('filter-available').checked) params.set('available', 'true');
  if (document.getElementById('filter-available-today').checked) params.set('availableToday', 'true');
  if (document.getElementById('filter-verified').checked) params.set('verified', 'true');
  if (marketplaceState.page > 0) params.set('page', String(marketplaceState.page + 1));
  const url = new URL(window.location.href);
  url.search = params.toString();
  window.history.replaceState({}, '', url);
}

function setUrlParam(params, key, value) {
  if (value != null && String(value).trim() !== '') params.set(key, String(value).trim());
}

function applyMarketplaceQueryParams() {
  const params = new URLSearchParams(window.location.search);
  document.getElementById('marketplace-search').value = params.get('q') || '';
  document.getElementById('marketplace-sort').value = params.get('sort') || 'top-rated';
  document.getElementById('filter-years').value = params.get('minExperience') || '';
  document.getElementById('filter-rating').value = params.get('minRating') || '';
  document.getElementById('filter-duration').value = params.get('sessionDuration') || '';
  document.getElementById('filter-available').checked = params.get('available') === 'true';
  document.getElementById('filter-available-today').checked = params.get('availableToday') === 'true';
  document.getElementById('filter-verified').checked = params.get('verified') === 'true';
  document.getElementById('filter-expertise')?.__multiSearchSelectControl?.setValues(splitFilterValues(params.get('expertise')));
  document.getElementById('filter-company')?.__searchSelectControl?.setValue(params.get('company') || '');
  document.getElementById('filter-language')?.__multiSearchSelectControl?.setValues(splitFilterValues(params.get('language')));
  document.getElementById('filter-timezone')?.__searchSelectControl?.setValue(params.get('timezone') || '');
  document.getElementById('filter-topic')?.__multiSearchSelectControl?.setValues(splitFilterValues(params.get('topic')));
  marketplaceState.page = Math.max(0, Number(params.get('page') || 1) - 1);
}

function controlValue(id) {
  const node = document.getElementById(id);
  if (!node) return '';
  if (node.__multiSearchSelectControl) return node.__multiSearchSelectControl.value();
  if (node.__searchSelectControl) return node.__searchSelectControl.value();
  return String(node.value || '').trim();
}

function controlValues(id) {
  const node = document.getElementById(id);
  if (!node) return [];
  if (node.__multiSearchSelectControl) return node.__multiSearchSelectControl.values();
  const value = controlValue(id);
  return value ? [value] : [];
}

function splitFilterValues(value) {
  return String(value || '').split(',').map(item => item.trim()).filter(Boolean);
}

function hydrateMarketplaceSeo(summary, items = []) {
  const title = 'InterviewPrep Marketplace | Browse Public Interviewers';
  const hasSummary = summary && Number(summary.interviewerCount || 0) > 0;
  const description = hasSummary
    ? `Compare ${formatCount(summary.interviewerCount)} public interviewers, ${formatCount(summary.verifiedCount)} verified experts, and live availability before booking your next mock interview.`
    : 'Browse public interviewer profiles, compare specialties, and discover live mock interview availability.';
  const url = publicPageUrl('/marketplace.html');
  const image = publicPageUrl('/assets/brand/interviewprep-logo.png');
  document.title = title;
  setMetaTag('name', 'description', description);
  setMetaTag('property', 'og:type', 'website');
  setMetaTag('property', 'og:title', title);
  setMetaTag('property', 'og:description', description);
  setMetaTag('property', 'og:url', window.location.href);
  setMetaTag('property', 'og:image', image);
  setMetaTag('name', 'twitter:card', 'summary_large_image');
  setMetaTag('name', 'twitter:title', title);
  setMetaTag('name', 'twitter:description', description);
  setMetaTag('name', 'twitter:image', image);
  setStructuredData('marketplace-structured-data', {
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: title,
    description,
    url,
    mainEntity: {
      '@type': 'ItemList',
      itemListElement: (Array.isArray(items) ? items : []).slice(0, 8).map((item, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        url: profileUrl(item.username),
        name: item.displayName || item.username || 'Interviewer',
      })),
    },
  });
}

function visiblePublicProfiles(items) {
  return (Array.isArray(items) ? items : []).filter(item => item?.publicProfileVisible !== false);
}

window.clearMarketplaceFilters = clearMarketplaceFilters;
window.changeMarketplacePage = changeMarketplacePage;
window.applyMarketplaceTopic = applyMarketplaceTopic;
