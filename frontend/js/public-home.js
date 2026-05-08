window.addEventListener('DOMContentLoaded', initPublicHome);

async function initPublicHome() {
  setCanonicalUrl(PUBLIC_SITE_URL ? `${PUBLIC_SITE_URL}/` : publicPageUrl('/index.html'));
  hydrateLandingSeo();
  primeLandingShell();

  try {
    const summary = await loadLandingSummary();
    renderLandingStats(summary);
    renderLandingFeatured(summary.featuredInterviewers || []);
    hydrateLandingSeo(summary);
    await renderLandingTestimonials(summary.featuredInterviewers || []);
  } catch (err) {
    renderLandingFallback(err.message || 'Could not load marketplace highlights right now.');
  }
}

function primeLandingShell() {
  const heroPreview = document.getElementById('hero-discovery-preview');
  const featured = document.getElementById('featured-interviewers-grid');
  const testimonials = document.getElementById('testimonial-grid');
  if (heroPreview) heroPreview.innerHTML = skeletonCards(3, 'marketplace-skeleton');
  if (featured) featured.innerHTML = skeletonCards(4, 'marketplace-skeleton');
  if (testimonials) testimonials.innerHTML = skeletonCards(3, 'review-skeleton');
}

function renderLandingStats(summary) {
  const metrics = [
    { id: 'stat-public-count', value: formatCount(summary.interviewerCount), label: 'public interviewers' },
    { id: 'stat-verified-count', value: formatCount(summary.verifiedCount), label: 'verified experts' },
    { id: 'stat-available-count', value: formatCount(summary.availableTodayCount), label: 'available today' },
    { id: 'stat-session-count', value: formatCount(summary.completedSessions), label: 'completed sessions' },
  ];
  metrics.forEach(metric => {
    const node = document.getElementById(metric.id);
    if (node) node.textContent = metric.value;
    const label = document.querySelector(`[data-label="${metric.id}"]`);
    if (label) label.textContent = metric.label;
  });
  const eyebrow = document.getElementById('hero-market-note');
  if (eyebrow) {
    eyebrow.textContent = `${formatCount(summary.availableCount)} interviewers are actively accepting new booking requests right now.`;
  }
}

function renderLandingFeatured(interviewers) {
  const preview = document.getElementById('hero-discovery-preview');
  const featured = document.getElementById('featured-interviewers-grid');
  const trust = document.getElementById('trust-signal-grid');
  const items = Array.isArray(interviewers) ? interviewers.slice(0, 4) : [];

  if (preview) {
    preview.innerHTML = items.slice(0, 3).map(renderPreviewCard).join('') || renderInlineEmpty('Featured interviewers will appear here automatically as public profiles go live.');
  }
  if (featured) {
    featured.innerHTML = items.map(renderFeaturedInterviewerCard).join('') || renderInlineEmpty('No public featured interviewers are available yet.');
  }
  if (trust) {
    trust.innerHTML = `
      <article class="proof-card">
        <strong>Verified profiles</strong>
        <p>Public listings highlight verification, review count, reliability, and live booking status before a candidate commits.</p>
      </article>
      <article class="proof-card">
        <strong>Real availability</strong>
        <p>Marketplace cards and public profiles surface actual slot previews instead of static “contact me” placeholders.</p>
      </article>
      <article class="proof-card">
        <strong>Structured reviews</strong>
        <p>Public feedback is topic-aware, giving candidates real signal on system design, coding, behavioral, and domain depth.</p>
      </article>
    `;
  }
}

async function renderLandingTestimonials(interviewers) {
  const node = document.getElementById('testimonial-grid');
  if (!node) return;
  const usernames = (Array.isArray(interviewers) ? interviewers : [])
    .map(item => item?.username)
    .filter(Boolean)
    .slice(0, 4);
  if (!usernames.length) {
    node.innerHTML = renderInlineEmpty('Public reviews will appear here as soon as interviewers publish them.');
    return;
  }
  const reviews = [];
  const results = await Promise.allSettled(usernames.map(username => publicApi(`/api/interviewers/public/${encodeURIComponent(username)}`)));
  results.forEach(result => {
    if (result.status !== 'fulfilled') return;
    const profile = result.value;
    const review = Array.isArray(profile.reviews) ? profile.reviews[0] : null;
    if (review) reviews.push({ review, profile });
  });
  node.innerHTML = reviews.slice(0, 3).map(({ review, profile }) => renderTestimonialCard(review, profile)).join('')
    || renderInlineEmpty('Published testimonials are still being collected from completed sessions.');
}

function renderPreviewCard(interviewer) {
  return `
    <a class="preview-person-card" href="${esc(profileUrl(interviewer.username))}">
      ${avatarMarkup(interviewer, 'avatar avatar-compact')}
      <div>
        <strong>${esc(interviewer.displayName || interviewer.username || 'Interviewer')}</strong>
        <p>${esc(interviewer.currentRole || 'Interview coach')} ${interviewer.company ? `at ${esc(interviewer.company)}` : ''}</p>
        <div class="tag-row subtle">${topicTags(interviewer.skills, 3)}</div>
      </div>
    </a>
  `;
}

function renderFeaturedInterviewerCard(interviewer) {
  const topics = Array.isArray(interviewer.interviewTopics) ? interviewer.interviewTopics.slice(0, 3) : [];
  return `
    <article class="featured-market-card">
      <div class="featured-market-head">
        ${avatarMarkup(interviewer, 'avatar avatar-profile')}
        <div>
          <div class="badge-row">
            ${interviewer.interviewerVerified ? '<span class="badge badge-green">Verified</span>' : '<span class="badge badge-gray">Public profile</span>'}
            <span class="badge badge-gray">${esc(availabilityLabel(interviewer))}</span>
          </div>
          <h3>${esc(interviewer.displayName || interviewer.username || 'Interviewer')}</h3>
          <p>${esc(interviewer.currentRole || 'Interview coach')} ${interviewer.company ? `at ${esc(interviewer.company)}` : ''}</p>
        </div>
      </div>
      <div class="stats-inline compact">
        <span>${esc(ratingLabel(interviewer.averageRating))}</span>
        <span>${formatCount(interviewer.reviewCount)} reviews</span>
        <span>${formatCount(interviewer.completedInterviews)} sessions</span>
      </div>
      <p class="market-card-bio">${esc(bioPreview(interviewer.bio, 138))}</p>
      <div class="tag-row">${topicTags(interviewer.skills, 4)}</div>
      ${topics.length ? `<div class="tag-row subtle">${topicTags(topics, 3)}</div>` : ''}
      <div class="featured-market-actions">
        <a class="btn btn-outline btn-sm" href="${esc(profileUrl(interviewer.username))}">View profile</a>
        <a class="btn btn-primary btn-sm" href="${esc(authUrl())}">Sign in to book</a>
      </div>
    </article>
  `;
}

function renderTestimonialCard(review, profile) {
  return `
    <article class="testimonial-card">
      <div class="testimonial-head">
        <div class="testimonial-identity">
          ${review.reviewerAvatarUrl ? `<span class="avatar avatar-compact has-image"><span class="avatar-fallback">${esc(initials(review.reviewerName))}</span><img src="${esc(review.reviewerAvatarUrl)}" alt="${esc(review.reviewerName || 'Reviewer')}" loading="lazy" decoding="async" onerror="this.parentElement.classList.remove('has-image'); this.remove()"></span>` : `<span class="avatar avatar-compact">${esc(initials(review.reviewerName))}</span>`}
          <div>
            <strong>${esc(review.reviewerName || 'InterviewPrep member')}</strong>
            <p>${esc(review.sessionTitle || 'Interview session')}</p>
          </div>
        </div>
        <span class="badge badge-green">${esc(ratingLabel(review.rating))}</span>
      </div>
      <p class="testimonial-quote">"${esc(bioPreview(review.comments, 190))}"</p>
      <div class="testimonial-foot">
        <span>${esc(profile.displayName || profile.username || 'Interviewer')}</span>
        <a href="${esc(profileUrl(profile.username))}">Open public profile</a>
      </div>
    </article>
  `;
}

function renderLandingFallback(message) {
  const blocks = ['hero-discovery-preview', 'featured-interviewers-grid', 'testimonial-grid'];
  blocks.forEach(id => {
    const node = document.getElementById(id);
    if (node) node.innerHTML = renderInlineEmpty(message);
  });
}

async function loadLandingSummary() {
  const page = await publicApi('/api/interviewers/search?sort=top-rated&page=0&size=12&publicOnly=true');
  const visible = visiblePublicProfiles(page.items || []);
  return {
    interviewerCount: Number(page.totalItems || visible.length || 0),
    verifiedCount: visible.filter(item => item.interviewerVerified).length,
    availableCount: visible.filter(isBookable).length,
    availableTodayCount: visible.filter(isBookable).length,
    completedSessions: visible.reduce((sum, item) => sum + Number(item.completedSessions || item.completedInterviews || 0), 0),
    featuredInterviewers: visible.slice(0, 4),
  };
}

function renderInlineEmpty(message) {
  return `<div class="empty-state empty-state-rich"><strong>Marketplace updates are loading</strong><p>${esc(message)}</p></div>`;
}

function hydrateLandingSeo(summary) {
  const title = 'InterviewPrep | Public Mock Interview Marketplace';
  const hasSummary = summary && Number(summary.interviewerCount || 0) > 0;
  const description = hasSummary
    ? `Browse ${formatCount(summary.interviewerCount)} public interviewers, compare verified experts, and book focused mock interviews with real availability.`
    : 'Browse verified mock interviewers, compare public profiles, and book focused practice sessions with real operators.';
  const url = PUBLIC_SITE_URL ? `${PUBLIC_SITE_URL}/` : publicPageUrl('/index.html');
  const image = publicPageUrl('/assets/brand/interviewprep-logo.png');
  document.title = title;
  setMetaTag('name', 'description', description);
  setMetaTag('property', 'og:type', 'website');
  setMetaTag('property', 'og:title', title);
  setMetaTag('property', 'og:description', description);
  setMetaTag('property', 'og:url', url);
  setMetaTag('property', 'og:image', image);
  setMetaTag('name', 'twitter:card', 'summary_large_image');
  setMetaTag('name', 'twitter:title', title);
  setMetaTag('name', 'twitter:description', description);
  setMetaTag('name', 'twitter:image', image);
  setStructuredData('landing-structured-data', {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'Organization',
        name: 'InterviewPrep',
        url,
        logo: image,
        description,
      },
      {
        '@type': 'WebSite',
        name: 'InterviewPrep',
        url,
        description,
        potentialAction: {
          '@type': 'SearchAction',
          target: `${publicPageUrl('/marketplace.html')}?q={search_term_string}`,
          'query-input': 'required name=search_term_string',
        },
      },
      {
        '@type': 'FAQPage',
        mainEntity: [
          {
            '@type': 'Question',
            name: 'How do candidates discover interviewers on InterviewPrep?',
            acceptedAnswer: { '@type': 'Answer', text: 'Candidates browse a public marketplace, compare skills and trust signals, then open a public profile before signing in to book.' },
          },
          {
            '@type': 'Question',
            name: 'Are availability previews and reviews shown publicly?',
            acceptedAnswer: { '@type': 'Answer', text: 'Yes. Public interviewer profiles can show upcoming availability, structured public reviews, and reliability indicators.' },
          },
        ],
      },
    ],
  });
}

function visiblePublicProfiles(items) {
  return (Array.isArray(items) ? items : []).filter(item => item?.publicProfileVisible !== false && item?.isPublicProfile !== false);
}
