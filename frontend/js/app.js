const API_BASE = window.INTERVIEW_API_BASE || '';
const USERNAME_PATTERN = /^[a-z0-9._-]{3,24}$/;

const authStore = {
  set(session) {
    if (session.user) localStorage.setItem('ip_user', JSON.stringify(session.user));
    if (session.accessToken) localStorage.setItem('ip_access_token', session.accessToken);
    if (session.refreshToken) localStorage.setItem('ip_refresh_token', session.refreshToken);
  },
  clear() {
    localStorage.removeItem('ip_user');
    localStorage.removeItem('ip_access_token');
    localStorage.removeItem('ip_refresh_token');
  },
  user() {
    try { return JSON.parse(localStorage.getItem('ip_user')); } catch { return null; }
  },
};

let resetEmail = '';
let usernameAvailabilityTimer = null;
let usernameAvailabilityState = { value: '', available: false };
let pendingVerificationEmail = '';

window.addEventListener('DOMContentLoaded', () => {
  const storedUser = authStore.user();
  const authEntry = isAuthEntryRoute();
  if (storedUser?.id && storedUser.isVerified !== false && !authEntry) {
    window.location.href = 'pages/dashboard.html';
    return;
  }
  setDisplay('interviewer-fields', 'none');
  setDisplay('reset-otp-group', 'none');
  setDisplay('reset-password-group', 'none');
  setDisplay('reset-resend', 'none');
  initRegisterControls();
  if (typeof FormUx !== 'undefined') FormUx.initPasswordToggles();
  bindUsernameValidation('reg-username', 'reg-username-status');
  updateRoleFields();
  if (storedUser?.id && storedUser.isVerified === false) {
    authStore.clear();
    beginEmailVerification(storedUser.email || '', {
      title: 'Verify your email.',
      body: 'Enter the code from your email or request a new one.',
      alert: 'Please verify your email before signing in.',
    });
    return;
  }
  const pendingEmail = sessionStorage.getItem('ip_pending_verification_email');
  if (pendingEmail) {
    beginEmailVerification(pendingEmail);
  }
  applyAuthEntryRoute();
});

document.querySelectorAll('#reg-role-group input').forEach(input => input.addEventListener('change', updateRoleFields));

function isAuthEntryRoute() {
  const path = window.location.pathname.replace(/\/+$/, '').toLowerCase();
  const hash = window.location.hash.replace(/^#\/?/, '').toLowerCase();
  return ['auth', 'login', 'register'].includes(hash) || ['/auth', '/login', '/register', '/index.html/auth'].includes(path);
}

function applyAuthEntryRoute() {
  const path = window.location.pathname.replace(/\/+$/, '').toLowerCase();
  const hash = window.location.hash.replace(/^#\/?/, '').toLowerCase();
  if (hash === 'register' || path === '/register') switchTab('register');
  if (['auth', 'login', 'register'].includes(hash) || ['/auth', '/login', '/register', '/index.html/auth'].includes(path)) {
    document.getElementById('auth')?.scrollIntoView({ block: 'start' });
  }
}

function setDisplay(id, value) {
  const el = document.getElementById(id);
  if (el) el.style.display = value;
}

function selectedRoles() {
  return Array.from(document.querySelectorAll('#reg-role-group input:checked')).map(input => input.value);
}

function updateRoleFields() {
  setDisplay('interviewer-fields', selectedRoles().includes('INTERVIEWER') ? 'block' : 'none');
}

function initRegisterControls() {
  if (typeof FormUx === 'undefined') return;
  FormUx.initLanguageSelect('reg-language', { placeholder: 'Search languages' });
  FormUx.initTagInput('reg-skills', { placeholder: 'Add expertise' });
}

function switchTab(tab) {
  ['login', 'register', 'verify', 'reset'].forEach(name => {
    document.getElementById(`tab-${name}`)?.classList.toggle('active', name === tab);
    document.getElementById(`panel-${name}`)?.classList.toggle('active', name === tab);
  });
}

function setVerifyCopy(title, body) {
  const root = document.getElementById('verify-copy');
  if (!root || typeof root.querySelector !== 'function') return;
  const strong = root.querySelector('strong');
  const span = root.querySelector('span');
  if (strong) strong.textContent = title;
  if (span) span.textContent = body;
}

function hideAlert(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = '';
  el.className = 'alert';
}

function beginEmailVerification(email, options = {}) {
  pendingVerificationEmail = String(email || '').trim();
  if (pendingVerificationEmail) {
    sessionStorage.setItem('ip_pending_verification_email', pendingVerificationEmail);
    const otpEmail = document.getElementById('otp-email');
    if (otpEmail) otpEmail.value = pendingVerificationEmail;
  }
  setVerifyCopy(
    options.title || 'Account created successfully.',
    options.body || 'We sent a verification code to your email.'
  );
  const otpCode = document.getElementById('otp-code');
  if (otpCode) otpCode.value = '';
  switchTab('verify');
  if (options.alert) {
    showAlert('otp-alert', options.alert, options.alertType || 'success');
  } else {
    hideAlert('otp-alert');
  }
  setTimeout(() => document.getElementById('otp-code')?.focus(), 50);
}

function completeEmailVerification(email) {
  const verifiedEmail = String(email || pendingVerificationEmail || '').trim();
  sessionStorage.removeItem('ip_pending_verification_email');
  pendingVerificationEmail = '';
  authStore.clear();
  const loginEmail = document.getElementById('login-email');
  const loginPassword = document.getElementById('login-password');
  if (loginEmail) loginEmail.value = verifiedEmail;
  if (loginPassword) loginPassword.value = '';
  switchTab('login');
  showAlert('login-alert', 'Email verified. You can sign in now.', 'success');
  setTimeout(() => document.getElementById('login-password')?.focus(), 50);
}

function isEmailLike(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || '').trim());
}

function isVerificationRequiredError(error) {
  return /verify your email/i.test(error?.message || '');
}

function showAlert(id, message, type = 'error') {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = message;
  el.className = `alert alert-${type} show`;
}

function toast(message, type = 'info') {
  const root = document.getElementById('toast-root');
  if (!root) return;
  const item = document.createElement('div');
  item.className = `toast toast-${type}`;
  item.textContent = message;
  root.appendChild(item);
  setTimeout(() => item.remove(), 4200);
}

function setLoading(btn, loading, label = 'Please wait') {
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

function bindUsernameValidation(inputId, statusId) {
  const input = document.getElementById(inputId);
  if (!input) return;
  input.addEventListener('input', () => {
    const normalized = normalizeUsernameInput(input.value);
    if (input.value !== normalized) input.value = normalized;
    usernameAvailabilityState = { value: normalized, available: false };
    clearTimeout(usernameAvailabilityTimer);
    const message = usernameValidationMessage(normalized);
    if (message) {
      setFieldValidation(statusId, message, 'error');
      return;
    }
    setFieldValidation(statusId, 'Checking availability...', 'neutral');
    usernameAvailabilityTimer = setTimeout(() => checkUsernameAvailability(normalized, statusId), 320);
  });
}

async function checkUsernameAvailability(username, statusId) {
  try {
    const result = await api(`/api/users/username-availability?username=${encodeURIComponent(username)}`);
    usernameAvailabilityState = { value: username, available: Boolean(result.available) };
    setFieldValidation(statusId, result.available ? 'Username available' : 'Username already taken', result.available ? 'success' : 'error');
  } catch (err) {
    usernameAvailabilityState = { value: username, available: false };
    setFieldValidation(statusId, err.message || 'Could not check username', 'error');
  }
}

async function validateUsernameBeforeSubmit(inputId, statusId) {
  const input = document.getElementById(inputId);
  const username = normalizeUsernameInput(input?.value);
  if (input && input.value !== username) input.value = username;
  const message = usernameValidationMessage(username);
  if (message) {
    setFieldValidation(statusId, message, 'error');
    return null;
  }
  if (usernameAvailabilityState.value === username && usernameAvailabilityState.available) {
    return username;
  }
  await checkUsernameAvailability(username, statusId);
  return usernameAvailabilityState.value === username && usernameAvailabilityState.available ? username : null;
}

async function api(path, options = {}, attempt = 0) {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const headers = { ...(options.headers || {}) };
  if (!isFormData && !headers['Content-Type'] && !headers['content-type']) {
    headers['Content-Type'] = 'application/json';
  }
  if (!API_BASE) throw new Error('API configuration is unavailable. Please refresh and try again.');
  const res = await fetchWithTimeout(`${API_BASE}${path}`, {
    ...options,
    headers,
  });
  const payload = await readPayload(res);
  if (shouldRetry(res, options, attempt)) {
    await delay(retryDelayMs(attempt, retryAfterSeconds(res)));
    return api(path, options, attempt + 1);
  }
  if (!res.ok) throw new Error(payload?.message || `Request failed (${res.status})`);
  return payload?.data ?? payload;
}

async function readPayload(res) {
  const text = await res.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 15000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (err) {
    if (err?.name === 'AbortError') throw new Error('Request timed out. Please try again.');
    throw err;
  } finally {
    clearTimeout(timer);
  }
}

function shouldRetry(res, options, attempt) {
  const method = String(options.method || 'GET').toUpperCase();
  if (attempt >= 2) return false;
  if (!['GET', 'HEAD'].includes(method)) return false;
  return res.status === 429 || res.status === 502 || res.status === 503 || res.status === 504;
}

function retryAfterSeconds(res) {
  const value = Number(res.headers.get('Retry-After'));
  return Number.isFinite(value) && value > 0 ? value : 0;
}

function retryDelayMs(attempt, retryAfter) {
  if (retryAfter > 0) return retryAfter * 1000;
  return 400 * (attempt + 1);
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

document.getElementById('login-form')?.addEventListener('submit', async event => {
  event.preventDefault();
  const btn = document.getElementById('login-submit');
  const identifier = document.getElementById('login-email').value.trim();
  setLoading(btn, true, 'Signing in');
  try {
    const session = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email: identifier,
        identifier,
        password: document.getElementById('login-password').value,
      }),
    });
    authStore.set(session);
    toast('Welcome back.', 'success');
    window.location.href = 'pages/dashboard.html';
  } catch (err) {
    if (isVerificationRequiredError(err) && isEmailLike(identifier)) {
      beginEmailVerification(identifier, {
        title: 'Verify your email.',
        body: 'Enter the code from your email or request a new one.',
        alert: 'Please verify your email before signing in.',
      });
      return;
    }
    showAlert('login-alert', err.message);
  } finally {
    setLoading(btn, false);
  }
});

document.getElementById('register-form')?.addEventListener('submit', async event => {
  event.preventDefault();
  const btn = document.getElementById('register-submit');
  const roles = selectedRoles();
  const skills = typeof FormUx !== 'undefined' ? FormUx.getTagValues('reg-skills') : [];
  if (!roles.length) {
    showAlert('register-alert', 'Select at least one workspace role.');
    return;
  }
  setLoading(btn, true, 'Creating account');
  try {
    const username = await validateUsernameBeforeSubmit('reg-username', 'reg-username-status');
    if (!username) return;
    const displayName = document.getElementById('reg-name').value.trim();
    await api('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        name: displayName,
        displayName,
        username,
        email: document.getElementById('reg-email').value.trim(),
        password: document.getElementById('reg-password').value,
        role: roles[0],
        roles,
        company: document.getElementById('reg-company').value.trim(),
        currentRole: document.getElementById('reg-current-role').value.trim(),
        yearsExperience: Number(document.getElementById('reg-years').value || 0),
        language: typeof FormUx !== 'undefined' ? FormUx.getLanguageString('reg-language') : '',
        skills,
      }),
    });
    authStore.clear();
    beginEmailVerification(document.getElementById('reg-email').value.trim());
  } catch (err) {
    showAlert('register-alert', err.message);
  } finally {
    setLoading(btn, false);
  }
});

document.getElementById('otp-form')?.addEventListener('submit', async event => {
  event.preventDefault();
  const btn = document.getElementById('otp-submit');
  const email = document.getElementById('otp-email').value.trim();
  setLoading(btn, true, 'Verifying');
  try {
    await api('/api/auth/verify-otp', {
      method: 'POST',
      body: JSON.stringify({
        email,
        otp: document.getElementById('otp-code').value.trim(),
      }),
    });
    showAlert('otp-alert', 'Email verified. Redirecting you to login...', 'success');
    setTimeout(() => completeEmailVerification(email), 800);
  } catch (err) {
    showAlert('otp-alert', err.message);
  } finally {
    setLoading(btn, false);
  }
});

async function resendOtp() {
  const btn = document.getElementById('otp-resend');
  setLoading(btn, true, 'Sending');
  try {
    await api('/api/auth/resend-otp', {
      method: 'POST',
      body: JSON.stringify({ email: document.getElementById('otp-email').value.trim() }),
    });
    showAlert('otp-alert', 'A fresh OTP has been sent.', 'success');
  } catch (err) {
    showAlert('otp-alert', err.message);
  } finally {
    setLoading(btn, false);
  }
}

function openResetPanel() {
  switchTab('reset');
  resetEmail = '';
  document.getElementById('reset-form')?.reset();
  setDisplay('forgot-email-group', 'flex');
  setDisplay('reset-otp-group', 'none');
  setDisplay('reset-password-group', 'none');
  setDisplay('reset-resend', 'none');
  const submit = document.getElementById('reset-submit');
  if (submit) submit.textContent = 'Send reset OTP';
}

document.getElementById('reset-form')?.addEventListener('submit', async event => {
  event.preventDefault();
  const btn = document.getElementById('reset-submit');
  setLoading(btn, true, 'Working');
  try {
    if (!resetEmail) {
      const email = document.getElementById('forgot-email').value.trim();
      await api('/api/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email }),
      });
      resetEmail = email;
      setDisplay('forgot-email-group', 'none');
      setDisplay('reset-otp-group', 'flex');
      setDisplay('reset-password-group', 'flex');
      setDisplay('reset-resend', 'inline-flex');
      document.getElementById('reset-submit').textContent = 'Reset password';
      showAlert('reset-alert', 'If that email exists, a reset OTP has been sent.', 'success');
      return;
    }
    const verified = await api('/api/auth/verify-reset-otp', {
      method: 'POST',
      body: JSON.stringify({
        email: resetEmail,
        otp: document.getElementById('reset-otp').value.trim(),
      }),
    });
    await api('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({
        token: verified.resetToken,
        newPassword: document.getElementById('reset-password').value,
      }),
    });
    showAlert('reset-alert', 'Password updated. You can sign in now.', 'success');
    setTimeout(() => switchTab('login'), 1000);
  } catch (err) {
    showAlert('reset-alert', err.message);
  } finally {
    setLoading(btn, false);
    if (resetEmail) {
      btn.textContent = 'Reset password';
    }
  }
});

async function resendResetOtp() {
  if (!resetEmail) return;
  try {
    await api('/api/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email: resetEmail }),
    });
    showAlert('reset-alert', 'A fresh reset OTP has been sent.', 'success');
  } catch (err) {
    showAlert('reset-alert', err.message);
  }
}

window.switchTab = switchTab;
window.openResetPanel = openResetPanel;
window.resendOtp = resendOtp;
window.resendResetOtp = resendResetOtp;
