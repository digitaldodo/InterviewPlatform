window.addEventListener('DOMContentLoaded', loadPublicProfile);

async function loadPublicProfile() {
  const root = document.getElementById('public-profile-root');
  const username = resolveUsername();
  if (!username) {
    root.innerHTML = renderProfileMessage('Interviewer profile not found.');
    return;
  }
  try {
    const profile = await publicApi(`/api/interviewers/public/${encodeURIComponent(username)}`);
    root.innerHTML = renderPublicProfile(profile);
    hydrateProfileSeo(profile);
  } catch (err) {
    root.innerHTML = renderProfileMessage(err.message || 'Could not load interviewer profile.');
  }
}

function resolveUsername() {
  const url = new URL(window.location.href);
  const query = url.searchParams.get('username');
  if (query) return query;
  const hash = url.hash.replace(/^#\/?/, '');
  if (hash) return hash;
  return '';
}

function renderPublicProfile(profile) {
  const topics = arrayValues(profile.interviewTopics, 4);
  const skills = arrayValues(profile.skills, 6);
  const domains = arrayValues(profile.preferredDomains, 5);
  const durations = arrayValues(profile.sessionDurations, 4);
  const reviews = Array.isArray(profile.reviews) ? profile.reviews : [];
  const availability = Array.isArray(profile.availabilityPreview) ? profile.availabilityPreview : [];
  const highlights = profileHighlights(profile);
  return `
    <section class="public-profile-hero">
      <div class="public-profile-hero-head">
        <a class="btn btn-outline btn-sm" href="${esc(marketplaceUrl())}">Back to marketplace</a>
        <div class="public-profile-actions">
          <button class="btn btn-outline btn-sm" type="button" onclick="copyPublicProfileLink()">Copy link</button>
          <button class="btn btn-outline btn-sm" type="button" onclick="sharePublicProfile()">Share</button>
          <a class="btn btn-primary btn-sm" href="${esc(authUrl())}">Sign in to book</a>
        </div>
      </div>
      <article class="public-profile-card profile-hero-card">
        <div class="public-profile-head">
          ${avatarMarkup(profile, 'avatar avatar-profile profile-hero-avatar')}
          <div class="public-profile-copy">
            <div class="profile-status-row">
              <span class="badge badge-purple">${esc(profile.currentRole || 'Interview coach')}</span>
              ${profile.interviewerVerified ? '<span class="badge badge-green">Verified interviewer</span>' : '<span class="badge badge-gray">Public profile</span>'}
              <span class="badge badge-gray">${esc(availabilityLabel(profile))}</span>
            </div>
            <h1>${esc(profile.displayName || profile.username || 'Interviewer')}</h1>
            <p>${esc(profile.company || 'InterviewPrep marketplace')}</p>
            <div class="stats-inline">
              <span>${esc(ratingLabel(profile.averageRating))}</span>
              <span>${formatCount(profile.reviewCount)} reviews</span>
              <span>${formatCount(profile.completedInterviews || profile.completedSessions)} completed sessions</span>
              <span>${esc(String(Number(profile.reliabilityScore || 0).toFixed(1)))}% reliability</span>
            </div>
            <div class="stats-inline">
              <span>${formatCount(profile.yearsExperience)}+ years</span>
              <span>${esc(profile.language || 'Language flexible')}</span>
              <span>${esc(profile.timeZone || 'Timezone flexible')}</span>
              ${durations.length ? `<span>${esc(durations.join(' / '))} min</span>` : ''}
            </div>
          </div>
        </div>
        <p class="public-profile-bio">${esc(profile.bio || 'This interviewer has not added a public bio yet, but their public trust signals and review history are available below.')}</p>
        <div class="tag-row">${skills.length ? skills.map(item => `<span>${esc(item)}</span>`).join('') : '<span>Mock interviews</span>'}</div>
        ${topics.length ? `<div class="tag-row subtle">${topics.map(item => `<span>${esc(item)}</span>`).join('')}</div>` : ''}
      </article>
    </section>

    <section class="content-grid public-profile-sections">
      <article class="panel public-profile-detail-card">
        <div class="panel-head"><h2>Profile highlights</h2><span class="badge badge-gray">${esc(profile.username || 'public')}</span></div>
        <div class="highlight-list">
          ${highlights.map(item => `<div class="highlight-item"><strong>${esc(item.label)}</strong><p>${esc(item.value)}</p></div>`).join('')}
        </div>
      </article>
      <article class="panel public-profile-detail-card">
        <div class="panel-head"><h2>Availability preview</h2><span class="badge badge-gray">${availability.length ? `${availability.length} slots` : 'No slots listed'}</span></div>
        <div class="public-slot-list">
          ${availability.map(slot => `
            <div class="public-slot-card">
              <strong>${esc(formatDate(slot, { weekday: 'short', month: 'short', day: 'numeric' }))}</strong>
              <span>${esc(formatDate(slot, { hour: 'numeric', minute: '2-digit' }))}</span>
            </div>
          `).join('') || '<div class="empty-state empty-state-rich"><strong>No upcoming availability</strong><p>This interviewer is public, but upcoming slots are not currently published.</p></div>'}
        </div>
        <a class="btn btn-outline btn-sm" href="${esc(authUrl())}">Sign in to request a slot</a>
      </article>
    </section>

    <section class="content-grid public-profile-sections">
      <article class="panel public-profile-detail-card">
        <div class="panel-head"><h2>Trust indicators</h2><span class="badge badge-green">${esc(profile.interviewerVerified ? 'Reviewed' : 'Open profile')}</span></div>
        <div class="trust-grid">
          <div class="trust-card">
            <strong>Reliability</strong>
            <p>${esc(String(Number(profile.reliabilityScore || 0).toFixed(1)))}% reliability across completed and cancelled sessions.</p>
          </div>
          <div class="trust-card">
            <strong>Review history</strong>
            <p>${formatCount(profile.reviewCount)} public reviews and ${formatCount(profile.completedInterviews || profile.completedSessions)} completed sessions.</p>
          </div>
          <div class="trust-card">
            <strong>Session coverage</strong>
            <p>${durations.length ? `${esc(durations.join(', '))} minute formats.` : 'Flexible session formats.'} ${profile.acceptingBookings === false ? 'Currently closed to new requests.' : 'Open to new booking requests.'}</p>
          </div>
        </div>
      </article>
      <article class="panel public-profile-detail-card">
        <div class="panel-head"><h2>Best fit topics</h2><span class="badge badge-purple">${domains.length ? `${domains.length} focus areas` : 'Generalist'}</span></div>
        ${domains.length ? `<div class="tag-row subtle">${domains.map(item => `<span>${esc(item)}</span>`).join('')}</div>` : '<p class="availability-muted">No public domain preferences are listed yet.</p>'}
        <div class="profile-link-rail">
          <a class="btn btn-outline btn-sm" href="${esc(marketplaceUrl({ topic: topics[0] || '' }))}">Find similar interviewers</a>
          <a class="btn btn-primary btn-sm" href="${esc(authUrl())}">Continue to sign in</a>
        </div>
      </article>
    </section>

    <section class="panel public-review-section">
      <div class="panel-head"><h2>Public reviews</h2><span class="badge badge-gray">${formatCount(reviews.length)} shown</span></div>
      <div class="public-review-grid">
        ${reviews.map(review => renderProfileReview(review, profile)).join('') || '<div class="empty-state empty-state-rich"><strong>No public reviews yet</strong><p>Public reviews appear here when completed sessions are shared on the marketplace.</p></div>'}
      </div>
    </section>
  `;
}

function renderProfileReview(review, profile) {
  const topics = Array.isArray(review.topicSummaries) ? review.topicSummaries : [];
  return `
    <article class="public-review-card">
      <div class="public-review-head">
        <div class="public-review-identity">
          ${review.reviewerAvatarUrl ? `<span class="avatar avatar-compact has-image"><span class="avatar-fallback">${esc(initials(review.reviewerName))}</span><img src="${esc(review.reviewerAvatarUrl)}" alt="${esc(review.reviewerName || 'Reviewer')}" loading="lazy" decoding="async" onerror="this.parentElement.classList.remove('has-image'); this.remove()"></span>` : `<span class="avatar avatar-compact">${esc(initials(review.reviewerName))}</span>`}
          <div>
            <strong>${esc(review.reviewerName || 'InterviewPrep member')}</strong>
            <small>${esc(review.sessionTitle || 'Interview session')} • ${esc(formatDate(review.createdAt, { dateStyle: 'medium' }))}</small>
          </div>
        </div>
        <div class="card-actions">
          <span class="badge badge-green">${esc(ratingLabel(review.rating))}</span>
          ${profile.interviewerVerified ? '<span class="badge badge-purple">Verified</span>' : ''}
        </div>
      </div>
      <p>${esc(review.comments || 'No written review was included.')}</p>
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

function profileHighlights(profile) {
  return [
    {
      label: 'Public link',
      value: profileUrl(profile.username),
    },
    {
      label: 'Booking status',
      value: profile.acceptingBookings === false ? 'Currently closed to new booking requests.' : 'Open to new booking requests.',
    },
    {
      label: 'Language',
      value: profile.language || 'Language flexible',
    },
    {
      label: 'Timezone',
      value: profile.timeZone || 'Timezone flexible',
    },
  ];
}

function hydrateProfileSeo(profile) {
  const title = `${profile.displayName || profile.username || 'Interviewer'} | InterviewPrep Profile`;
  const description = `${profile.displayName || profile.username || 'Interviewer'}${profile.currentRole ? `, ${profile.currentRole}` : ''}${profile.company ? ` at ${profile.company}` : ''}. Compare public reviews, trust indicators, and live availability on InterviewPrep.`;
  const url = profileUrl(profile.username || resolveUsername());
  const image = String(profile.avatarUrl || publicPageUrl('/assets/brand/interviewprep-logo.png'));
  document.title = title;
  setCanonicalUrl(url);
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
  setStructuredData('profile-structured-data', {
    '@context': 'https://schema.org',
    '@type': 'Person',
    name: profile.displayName || profile.username || 'Interviewer',
    url,
    image,
    description,
    jobTitle: profile.currentRole || undefined,
    worksFor: profile.company ? { '@type': 'Organization', name: profile.company } : undefined,
    knowsAbout: [...arrayValues(profile.skills, 12), ...arrayValues(profile.interviewTopics, 12)].slice(0, 12),
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

async function copyPublicProfileLink() {
  try {
    await navigator.clipboard.writeText(document.querySelector('link[rel="canonical"]')?.href || window.location.href);
    showProfileToast('Profile link copied.');
  } catch {
    showProfileToast('Copy failed on this device.');
  }
}

async function sharePublicProfile() {
  const url = document.querySelector('link[rel="canonical"]')?.href || window.location.href;
  if (navigator.share) {
    try {
      await navigator.share({ title: document.title, url });
      return;
    } catch {}
  }
  await copyPublicProfileLink();
}

function renderProfileMessage(message) {
  return `<div class="panel"><div class="empty-state empty-state-rich"><strong>Profile unavailable</strong><p>${esc(message)}</p><a class="btn btn-outline btn-sm" href="${esc(marketplaceUrl())}">Browse marketplace</a></div></div>`;
}

function arrayValues(value, limit = 6) {
  return Array.isArray(value) ? value.filter(Boolean).slice(0, limit) : [];
}

function showProfileToast(message) {
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

window.copyPublicProfileLink = copyPublicProfileLink;
window.sharePublicProfile = sharePublicProfile;
