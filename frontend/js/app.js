const API_BASE = window.INTERVIEW_API_BASE || 'http://localhost:8080';

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

window.addEventListener('DOMContentLoaded', () => {
  if (authStore.user()?.id) window.location.href = 'pages/dashboard.html';
  document.getElementById('interviewer-fields').style.display = 'none';
  document.getElementById('reset-token-group').style.display = 'none';
  document.getElementById('reset-password-group').style.display = 'none';
  const resetToken = new URLSearchParams(window.location.search).get('resetToken');
  if (resetToken) openResetPanel(resetToken);
});

document.getElementById('reg-role').addEventListener('change', function () {
  document.getElementById('interviewer-fields').style.display = this.value === 'interviewer' ? 'block' : 'none';
});

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

async function api(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  const payload = await readPayload(res);
  if (!res.ok) throw new Error(payload?.message || `Request failed (${res.status})`);
  return payload?.data ?? payload;
}

async function readPayload(res) {
  const text = await res.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
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
  const role = document.getElementById('reg-role').value;
  const skillsRaw = document.getElementById('reg-skills').value;
  setLoading(btn, true, 'Creating account');
  try {
    const session = await api('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        name: document.getElementById('reg-name').value.trim(),
        email: document.getElementById('reg-email').value.trim(),
        password: document.getElementById('reg-password').value,
        role,
        company: document.getElementById('reg-company').value.trim(),
        currentRole: document.getElementById('reg-current-role').value.trim(),
        yearsExperience: Number(document.getElementById('reg-years').value || 0),
        language: document.getElementById('reg-language').value.trim(),
        skills: skillsRaw.split(',').map(item => item.trim()).filter(Boolean),
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

function openResetPanel(token = '') {
  switchTab('reset');
  const hasToken = Boolean(token);
  document.getElementById('reset-token').value = token;
  document.getElementById('forgot-email-group').style.display = hasToken ? 'none' : 'flex';
  document.getElementById('reset-token-group').style.display = hasToken ? 'flex' : 'none';
  document.getElementById('reset-password-group').style.display = hasToken ? 'flex' : 'none';
  document.getElementById('reset-submit').textContent = hasToken ? 'Reset password' : 'Send reset link';
}

document.getElementById('reset-form').addEventListener('submit', async event => {
  event.preventDefault();
  const token = document.getElementById('reset-token').value.trim();
  try {
    if (!token) {
      await api('/api/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email: document.getElementById('forgot-email').value.trim() }),
      });
      showAlert('reset-alert', 'If that email exists, a reset link has been sent.', 'success');
      return;
    }
    await api('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({
        token,
        newPassword: document.getElementById('reset-password').value,
      }),
    });
    showAlert('reset-alert', 'Password updated. You can sign in now.', 'success');
    setTimeout(() => switchTab('login'), 1000);
  } catch (err) {
    showAlert('reset-alert', err.message);
  }
});
