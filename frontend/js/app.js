const API_BASE = window.INTERVIEW_API_BASE;
const USERNAME_PATTERN = /^[a-z0-9._-]{3,24}$/;

const authStore = {
  set(session) {
    if (session.user) localStorage.setItem('ip_user', JSON.stringify(session.user));
    if (session.accessToken) localStorage.setItem('ip_access_token', session.accessToken);
    if (session.refreshToken) localStorage.setItem('ip_refresh_token', session.refreshToken);
  },
  user() {
    try { return JSON.parse(localStorage.getItem('ip_user')); } catch { return null; }
  },
};

let resetEmail = '';
let usernameAvailabilityTimer = null;
let usernameAvailabilityState = { value: '', available: false };

window.addEventListener('DOMContentLoaded', () => {
  if (authStore.user()?.id) window.location.href = 'pages/dashboard.html';
  document.getElementById('interviewer-fields').style.display = 'none';
  document.getElementById('reset-otp-group').style.display = 'none';
  document.getElementById('reset-password-group').style.display = 'none';
  document.getElementById('reset-resend').style.display = 'none';
  initRegisterControls();
  FormUx.initPasswordToggles();
  bindUsernameValidation('reg-username', 'reg-username-status');
  updateRoleFields();
});

document.querySelectorAll('#reg-role-group input').forEach(input => input.addEventListener('change', updateRoleFields));

function selectedRoles() {
  return Array.from(document.querySelectorAll('#reg-role-group input:checked')).map(input => input.value);
}

function updateRoleFields() {
  document.getElementById('interviewer-fields').style.display = selectedRoles().includes('INTERVIEWER') ? 'block' : 'none';
}

function initRegisterControls() {
  FormUx.initLanguageSelect('reg-language', { placeholder: 'Search languages' });
  FormUx.initTagInput('reg-skills', { placeholder: 'Add expertise' });
}

function switchTab(tab) {
  ['login', 'register', 'verify', 'reset'].forEach(name => {
    document.getElementById(`tab-${name}`)?.classList.toggle('active', name === tab);
    document.getElementById(`panel-${name}`)?.classList.toggle('active', name === tab);
  });
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

document.getElementById('login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const btn = document.getElementById('login-submit');
  setLoading(btn, true, 'Signing in');
  try {
    const session = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email: document.getElementById('login-email').value.trim(),
        identifier: document.getElementById('login-email').value.trim(),
        password: document.getElementById('login-password').value,
      }),
    });
    authStore.set(session);
    toast('Welcome back.', 'success');
    window.location.href = 'pages/dashboard.html';
  } catch (err) {
    showAlert('login-alert', err.message);
  } finally {
    setLoading(btn, false);
  }
});

document.getElementById('register-form').addEventListener('submit', async event => {
  event.preventDefault();
  const btn = document.getElementById('register-submit');
  const roles = selectedRoles();
  const skills = FormUx.getTagValues('reg-skills');
  if (!roles.length) {
    showAlert('register-alert', 'Select at least one workspace role.');
    return;
  }
  setLoading(btn, true, 'Creating account');
  try {
    const username = await validateUsernameBeforeSubmit('reg-username', 'reg-username-status');
    if (!username) return;
    const displayName = document.getElementById('reg-name').value.trim();
    const session = await api('/api/auth/register', {
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
        language: FormUx.getLanguageString('reg-language'),
        skills,
      }),
    });
    authStore.set(session);
    document.getElementById('otp-email').value = document.getElementById('reg-email').value.trim();
    switchTab('verify');
    showAlert('otp-alert', 'Account created. Check your email for the OTP.', 'success');
  } catch (err) {
    showAlert('register-alert', err.message);
  } finally {
    setLoading(btn, false);
  }
});

document.getElementById('otp-form').addEventListener('submit', async event => {
  event.preventDefault();
  const btn = document.getElementById('otp-submit');
  setLoading(btn, true, 'Verifying');
  try {
    await api('/api/auth/verify-otp', {
      method: 'POST',
      body: JSON.stringify({
        email: document.getElementById('otp-email').value.trim(),
        otp: document.getElementById('otp-code').value.trim(),
      }),
    });
    const user = authStore.user();
    if (user) {
      user.isVerified = true;
      localStorage.setItem('ip_user', JSON.stringify(user));
    }
    window.location.href = 'pages/dashboard.html';
  } catch (err) {
    showAlert('otp-alert', err.message);
  } finally {
    setLoading(btn, false);
  }
});

async function resendOtp() {
  try {
    await api('/api/auth/resend-otp', {
      method: 'POST',
      body: JSON.stringify({ email: document.getElementById('otp-email').value.trim() }),
    });
    showAlert('otp-alert', 'A fresh OTP has been sent.', 'success');
  } catch (err) {
    showAlert('otp-alert', err.message);
  }
}

function openResetPanel() {
  switchTab('reset');
  resetEmail = '';
  document.getElementById('reset-form').reset();
  document.getElementById('forgot-email-group').style.display = 'flex';
  document.getElementById('reset-otp-group').style.display = 'none';
  document.getElementById('reset-password-group').style.display = 'none';
  document.getElementById('reset-resend').style.display = 'none';
  document.getElementById('reset-submit').textContent = 'Send reset OTP';
}

document.getElementById('reset-form').addEventListener('submit', async event => {
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
      document.getElementById('forgot-email-group').style.display = 'none';
      document.getElementById('reset-otp-group').style.display = 'flex';
      document.getElementById('reset-password-group').style.display = 'flex';
      document.getElementById('reset-resend').style.display = 'inline-flex';
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
