const API_BASE = window.INTERVIEW_API_BASE;

window.addEventListener('DOMContentLoaded', loadPublicProfile);

async function loadPublicProfile() {
  const root = document.getElementById('public-profile-root');
  const username = resolveUsername();
  if (!username) {
    root.innerHTML = renderMessage('Interviewer profile not found.');
    return;
  }
  try {
    const profile = await api(`/api/interviewers/public/${encodeURIComponent(username)}`);
    setDocumentMeta(profile);
    root.innerHTML = renderProfile(profile);
  } catch (err) {
    root.innerHTML = renderMessage(err.message || 'Could not load interviewer profile.');
  }
}

function resolveUsername() {
  const url = new URL(window.location.href);
  const query = url.searchParams.get('username');
  if (query) return query;
  const hash = window.location.hash.replace(/^#\/?/, '');
  if (hash) return hash;
  const parts = url.pathname.split('/').filter(Boolean);
  return parts[parts.length - 1] === 'interviewer.html' ? '' : parts[parts.length - 1];
}

async function api(path) {
  const res = await fetch(`${API_BASE}${path}`);
  const payload = await readPayload(res);
  if (!res.ok) throw new Error(payload?.message || `Request failed (${res.status})`);
  return payload?.data ?? payload;
}

async function readPayload(res) {
  const text = await res.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
}

function renderProfile(profile) {
  const topics = Array.isArray(profile.interviewTopics) ? profile.interviewTopics : [];
  const skills = Array.isArray(profile.skills) ? profile.skills : [];
  const domains = Array.isArray(profile.preferredDomains) ? profile.preferredDomains : [];
  const durations = Array.isArray(profile.sessionDurations) ? profile.sessionDurations : [];
  const reviews = Array.isArray(profile.reviews) ? profile.reviews : [];
  const slots = Array.isArray(profile.availabilityPreview) ? profile.availabilityPreview : [];
  const reliability = Number(profile.reliabilityScore || 0);
  const publicUrl = canonicalProfileUrl(profile);
  return `
    <section class="public-profile-hero">
      <div class="public-profile-hero-head">
        <a class="btn btn-outline btn-sm" href="../index.html">Back to InterviewPrep</a>
        <div class="public-profile-actions">
          <a class="btn btn-outline btn-sm" href="./dashboard.html#/booking">Book session</a>
          <button class="btn btn-outline btn-sm" type="button" onclick="copyProfileLink()">Copy link</button>
          <button class="btn btn-primary btn-sm" type="button" onclick="shareProfileLink()">Share</button>
        </div>
      </div>
      <article class="public-profile-card">
        <div class="public-profile-head">
          ${avatarMarkup(profile)}
          <div class="public-profile-copy">
            <div class="profile-status-row">
              <span class="badge badge-purple">${esc(profile.currentRole || 'Interview coach')}</span>
              ${profile.interviewerVerified ? '<span class="badge badge-green">Verified interviewer</span>' : ''}
              <span class="badge badge-gray">${esc(String(reliability.toFixed(1)))}% reliability</span>
            </div>
            <h1>${esc(profile.displayName || profile.username || 'Interviewer')}</h1>
            <p>${esc(profile.company || 'InterviewPrep marketplace')}</p>
            <div class="stats-inline">
              <span>${ratingStars(profile.averageRating)} ${esc(String(profile.averageRating || 0))}/5</span>
              <span>${esc(String(profile.reviewCount || reviews.length || 0))} reviews</span>
              <span>${esc(String(profile.completedInterviews || 0))} completed sessions</span>
            </div>
            <div class="stats-inline">
              <span>${esc(String(profile.yearsExperience || 0))}+ years experience</span>
              <span>${esc(profile.language || 'Language flexible')}</span>
              <span>${esc(profile.timeZone || 'Timezone flexible')}</span>
              ${durations.length ? `<span>${esc(durations.join(' / '))} min</span>` : ''}
            </div>
          </div>
        </div>
        <p class="public-profile-bio">${esc(profile.bio || 'No bio available yet.')}</p>
        ${skills.length ? `<div class="tag-row">${skills.map(skill => `<span>${esc(skill)}</span>`).join('')}</div>` : ''}
        ${topics.length ? `<div class="tag-row subtle">${topics.map(topic => `<span>${esc(topic)}</span>`).join('')}</div>` : ''}
        <div class="public-profile-detail-grid">
          <div class="public-profile-detail-card">
            <strong>Focus areas</strong>
            ${domains.length
              ? `<div class="tag-row subtle">${domains.map(domain => `<span>${esc(domain)}</span>`).join('')}</div>`
              : '<p>No focus areas published yet.</p>'}
          </div>
          <div class="public-profile-detail-card">
            <strong>Profile link</strong>
            <p><a href="${esc(publicUrl)}" target="_blank" rel="noreferrer">${esc(publicUrl)}</a></p>
          </div>
        </div>
      </article>
    </section>
    <section class="content-grid public-profile-sections">
      <article class="panel">
        <div class="panel-head"><h2>Availability preview</h2><span class="badge badge-gray">${esc(slots.length ? `${slots.length} upcoming slots` : 'No slots listed')}</span></div>
        <div class="public-slot-list">
          ${slots.map(slot => `
            <div class="public-slot-card">
              <strong>${esc(formatDate(slot))}</strong>
              <span>${esc(formatTimeWindow(slot))}</span>
            </div>
          `).join('') || '<div class="empty-state empty-state-rich"><strong>No upcoming availability</strong><p>This interviewer has not published upcoming slots yet. You can still follow and revisit soon.</p></div>'}
        </div>
      </article>
      <article class="panel">
        <div class="panel-head"><h2>Trust signals</h2><span class="badge badge-gray">${esc(String(profile.completedSessions || profile.completedInterviews || 0))} total sessions</span></div>
        <div class="trust-grid">
          <div class="trust-card">
            <strong>Review quality</strong>
            <p>${esc(String(profile.reviewCount || reviews.length || 0))} public reviews with an average rating of ${esc(String(profile.averageRating || 0))}/5.</p>
          </div>
          <div class="trust-card">
            <strong>Reliability</strong>
            <p>${esc(String(reliability.toFixed(1)))}% session reliability based on completed versus cancelled sessions (${esc(String(profile.cancelledSessions || 0))} cancelled).</p>
          </div>
          <div class="trust-card">
            <strong>Booking status</strong>
            <p>${profile.acceptingBookings === false ? 'Currently not accepting new bookings.' : 'Open to new booking requests.'}</p>
          </div>
        </div>
      </article>
      <article class="panel">
        <div class="panel-head"><h2>Share and actions</h2><span class="badge badge-purple">Public profile</span></div>
        <div class="share-rail">
          <button class="btn btn-outline" type="button" onclick="copyProfileLink()">Copy profile link</button>
          <button class="btn btn-outline" type="button" onclick="shareProfileLink()">Share profile</button>
          <a class="btn btn-primary" href="./dashboard.html#/booking">Book this interviewer</a>
        </div>
      </article>
    </section>
    <section class="panel public-review-section">
      <div class="panel-head"><h2>Interviewer reviews</h2><span class="badge badge-gray">${esc(String(profile.reviewCount || reviews.length || 0))} reviews</span></div>
      <div class="public-review-grid">
        ${reviews.map(review => renderReviewCard(review, profile)).join('') || '<div class="empty-state empty-state-rich"><strong>No public reviews yet</strong><p>Once sessions are completed and reviews are shared publicly, they will appear here.</p></div>'}
      </div>
    </section>
  `;
}

function renderReviewCard(review, profile) {
  const topics = Array.isArray(review.topicSummaries) ? review.topicSummaries : [];
  const reliability = Number(profile?.reliabilityScore || 0);
  return `
    <article class="public-review-card">
      <div class="public-review-head">
        <div class="public-review-identity">
          ${review.reviewerAvatarUrl ? `<span class="avatar avatar-compact has-image"><span class="avatar-fallback">${esc(String((review.reviewerName || 'Reviewer').charAt(0).toUpperCase()))}</span><img src="${esc(review.reviewerAvatarUrl)}" alt="${esc(review.reviewerName || 'Reviewer')}" loading="lazy" decoding="async" onerror="this.parentElement.classList.remove('has-image'); this.remove()"></span>` : '<div class="avatar avatar-compact">R</div>'}
          <div>
            <strong>${esc(review.reviewerName || 'InterviewPrep member')}</strong>
            <small>${esc(review.sessionTitle || 'Interview session')} • ${esc(formatDate(review.createdAt))}</small>
          </div>
        </div>
        <div class="card-actions">
          <span class="badge badge-green">${ratingStars(review.rating)} ${esc(String(review.rating || 0))}/5</span>
          ${profile?.interviewerVerified ? '<span class="badge badge-purple">Verified interviewer</span>' : ''}
        </div>
      </div>
      <p>${esc(review.comments || 'No written review provided.')}</p>
      <div class="tag-row subtle">
        <span>Reliability ${esc(String(reliability.toFixed(1)))}%</span>
        <span>Cancelled ${esc(String(profile?.cancelledSessions || 0))}</span>
      </div>
      ${topics.length ? `
        <div class="public-topic-feedback-grid">
          ${topics.map(topic => `
            <details class="public-topic-feedback-card">
              <summary>${esc(topic.topic || 'Topic')} ${topic.rating ? `<strong>${esc(String(topic.rating))}/5</strong>` : ''}</summary>
              ${(topic.skillRatings && Object.keys(topic.skillRatings).length)
                ? `<div class="tag-row subtle">${Object.entries(topic.skillRatings).map(([name, value]) => `<span>${esc(name)} ${esc(String(value || 0))}/5</span>`).join('')}</div>`
                : ''}
              ${topic.strengths ? `<p><strong>Strength:</strong> ${esc(topic.strengths)}</p>` : ''}
              ${topic.improvementAreas ? `<p><strong>Improve:</strong> ${esc(topic.improvementAreas)}</p>` : ''}
              ${topic.comments ? `<p><strong>Comment:</strong> ${esc(topic.comments)}</p>` : ''}
            </details>
          `).join('')}
        </div>
      ` : ''}
    </article>
  `;
}

function setDocumentMeta(profile) {
  const title = `${profile.displayName || profile.username || 'Interviewer'} • InterviewPrep`;
  document.title = title;
  const description = `${profile.displayName || profile.username || 'Interviewer'}${profile.currentRole ? `, ${profile.currentRole}` : ''}${profile.company ? ` at ${profile.company}` : ''}. Book InterviewPrep practice sessions and review live availability.`;
  const url = canonicalProfileUrl(profile);
  const image = String(profile.avatarUrl || `${window.location.origin}/assets/brand/interviewprep-logo.png`);
  setMetaTag('name', 'description', description);
  setMetaTag('property', 'og:type', 'profile');
  setMetaTag('property', 'og:title', title);
  setMetaTag('property', 'og:description', description);
  setMetaTag('property', 'og:url', url);
  setMetaTag('property', 'og:image', image);
  setMetaTag('property', 'profile:username', profile.username || '');
  setMetaTag('name', 'twitter:card', 'summary_large_image');
  setMetaTag('name', 'twitter:title', title);
  setMetaTag('name', 'twitter:description', description);
  setMetaTag('name', 'twitter:image', image);
  setCanonicalUrl(url);

  let script = document.getElementById('profile-structured-data');
  if (!script) {
    script = document.createElement('script');
    script.type = 'application/ld+json';
    script.id = 'profile-structured-data';
    document.head.appendChild(script);
  }
  script.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Person',
    name: profile.displayName || profile.username || 'Interviewer',
    jobTitle: profile.currentRole || undefined,
    worksFor: profile.company ? { '@type': 'Organization', name: profile.company } : undefined,
    image: profile.avatarUrl || undefined,
    description,
    url,
    aggregateRating: Number(profile.reviewCount || 0) > 0
      ? {
          '@type': 'AggregateRating',
          ratingValue: Number(profile.averageRating || 0),
          reviewCount: Number(profile.reviewCount || 0),
          bestRating: 5,
          worstRating: 1,
        }
      : undefined,
  });
}

async function copyProfileLink() {
  try {
    await navigator.clipboard.writeText(currentCanonicalUrl());
    toast('Profile link copied.');
  } catch {
    toast('Copy failed on this device.');
  }
}

async function shareProfileLink() {
  if (navigator.share) {
    try {
      await navigator.share({ title: document.title, url: currentCanonicalUrl() });
      return;
    } catch {}
  }
  await copyProfileLink();
}

function renderMessage(message) {
  return `<div class="panel"><p>${esc(message)}</p></div>`;
}

function avatarMarkup(profile) {
  const name = profile.displayName || profile.username || 'Interviewer';
  if (profile.avatarUrl) {
    return `<span class="avatar large has-image"><span class="avatar-fallback">${esc(name.charAt(0).toUpperCase())}</span><img src="${esc(profile.avatarUrl)}" alt="${esc(name)}" loading="lazy" decoding="async" onerror="this.parentElement.classList.remove('has-image'); this.remove()"></span>`;
  }
  return `<div class="avatar large">${esc(name.charAt(0).toUpperCase())}</div>`;
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

function canonicalProfileUrl(profile) {
  const base = new URL(window.location.href);
  const username = encodeURIComponent(profile?.username || resolveUsername() || '');
  base.hash = '';
  base.search = '';
  if (username) base.search = `?username=${username}`;
  return base.toString();
}

function currentCanonicalUrl() {
  const canonical = document.querySelector('link[rel="canonical"]')?.getAttribute('href');
  return canonical || window.location.href;
}

function ratingStars(value) {
  const count = Math.max(0, Math.min(5, Math.round(Number(value || 0))));
  return '★'.repeat(count) + '☆'.repeat(Math.max(0, 5 - count));
}

function formatDate(value) {
  if (!value) return 'Flexible';
  try {
    return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  } catch {
    return String(value);
  }
}

function formatTimeWindow(value) {
  if (!value) return 'Flexible timing';
  try {
    return new Date(value).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  } catch {
    return String(value);
  }
}

function toast(message) {
  const root = document.getElementById('public-profile-toast') || (() => {
    const div = document.createElement('div');
    div.id = 'public-profile-toast';
    div.className = 'toast public-profile-toast';
    document.body.appendChild(div);
    return div;
  })();
  root.textContent = message;
  root.classList.add('show');
  clearTimeout(root.__timer);
  root.__timer = setTimeout(() => root.classList.remove('show'), 2200);
}

function esc(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}
