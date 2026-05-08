const PUBLIC_API_BASE = window.INTERVIEW_API_BASE;
const PUBLIC_SITE_URL = resolvePublicSiteUrl();

function resolvePublicSiteUrl() {
  const configured = String(window.INTERVIEW_SITE_URL || '').trim();
  if (configured) return configured.replace(/\/+$/, '');
  if (/^https?:$/i.test(window.location.protocol)) return window.location.origin.replace(/\/+$/, '');
  return '';
}

async function publicApi(path, options = {}) {
  const res = await fetch(`${PUBLIC_API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  const payload = await readPublicPayload(res);
  if (!res.ok) throw new Error(payload?.message || `Request failed (${res.status})`);
  return payload?.data ?? payload;
}

async function readPublicPayload(res) {
  const text = await res.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
}

function publicPageUrl(path) {
  if (PUBLIC_SITE_URL) return new URL(path.replace(/^\//, ''), `${PUBLIC_SITE_URL}/`).toString();
  return new URL(path.replace(/^\//, ''), window.location.href).toString();
}

function marketplaceUrl(params = {}) {
  const url = new URL(publicPageUrl('/marketplace.html'));
  Object.entries(params).forEach(([key, value]) => {
    if (value != null && String(value).trim() !== '') url.searchParams.set(key, String(value).trim());
  });
  return url.toString();
}

function profileUrl(username) {
  const value = String(username || '').trim();
  if (!value) return publicPageUrl('/pages/interviewer.html');
  return publicPageUrl(`/interviewer/${encodeURIComponent(value)}`);
}

function authUrl() {
  return `${publicPageUrl('/index.html')}#auth`;
}

function initials(value) {
  return String(value || 'Interview Prep')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(item => item.charAt(0).toUpperCase())
    .join('') || 'IP';
}

function esc(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

function avatarMarkup(person, className = 'avatar avatar-profile') {
  const label = person?.displayName || person?.username || 'Interviewer';
  if (person?.avatarUrl) {
    return `<span class="${esc(className)} has-image"><span class="avatar-fallback">${esc(initials(label))}</span><img src="${esc(person.avatarUrl)}" alt="${esc(label)}" loading="lazy" decoding="async" onerror="this.parentElement.classList.remove('has-image'); this.remove()"></span>`;
  }
  return `<span class="${esc(className)}">${esc(initials(label))}</span>`;
}

function ratingStars(value) {
  const count = Math.max(0, Math.min(5, Math.round(Number(value || 0))));
  return '★'.repeat(count) + '☆'.repeat(Math.max(0, 5 - count));
}

function ratingLabel(value) {
  const numeric = Number(value || 0);
  if (!numeric) return 'New reviewer';
  return `${numeric.toFixed(1)} ${ratingStars(numeric)}`;
}

function bioPreview(value, max = 148) {
  const text = String(value || '').trim();
  if (!text) return 'Focused mock interviews with practical feedback and real-world interview context.';
  return text.length > max ? `${text.slice(0, max).trimEnd()}...` : text;
}

function formatDate(value, options = { dateStyle: 'medium' }) {
  if (!value) return 'Flexible';
  try {
    return new Date(value).toLocaleString(undefined, options);
  } catch {
    return String(value);
  }
}

function formatCount(value) {
  return new Intl.NumberFormat().format(Number(value || 0));
}

function formatDurationList(values) {
  const items = Array.isArray(values) ? values.filter(Boolean) : [];
  return items.length ? `${items.join(' / ')} min` : 'Flexible duration';
}

function availabilityLabel(interviewer) {
  if (interviewer?.hasAvailability === false) return 'Availability not added yet';
  if (interviewer?.acceptingBookings === false) return 'Not accepting bookings';
  return 'Available for booking';
}

function isBookable(interviewer) {
  if (!interviewer || interviewer.acceptingBookings === false) return false;
  if (Object.prototype.hasOwnProperty.call(interviewer, 'bookable')) return Boolean(interviewer.bookable);
  if (Object.prototype.hasOwnProperty.call(interviewer, 'hasAvailability')) return Boolean(interviewer.hasAvailability);
  return true;
}

function setMetaTag(attribute, key, content) {
  let node = document.querySelector(`meta[${attribute}="${key}"]`);
  if (!node) {
    node = document.createElement('meta');
    node.setAttribute(attribute, key);
    document.head.appendChild(node);
  }
  node.setAttribute('content', String(content || ''));
}

function setCanonicalUrl(url) {
  let node = document.querySelector('link[rel="canonical"]');
  if (!node) {
    node = document.createElement('link');
    node.setAttribute('rel', 'canonical');
    document.head.appendChild(node);
  }
  node.setAttribute('href', url);
}

function setStructuredData(id, payload) {
  let node = document.getElementById(id);
  if (!node) {
    node = document.createElement('script');
    node.type = 'application/ld+json';
    node.id = id;
    document.head.appendChild(node);
  }
  node.textContent = JSON.stringify(payload);
}

function skeletonCards(count, variant = '') {
  const extra = variant ? ` ${variant}` : '';
  return Array.from({ length: count }).map(() => `<div class="skeleton-card${extra}"></div>`).join('');
}

function topicTags(values, limit = 4) {
  const items = Array.isArray(values) ? values.filter(Boolean).slice(0, limit) : [];
  return items.length ? items.map(item => `<span>${esc(item)}</span>`).join('') : '<span>Mock interviews</span>';
}
