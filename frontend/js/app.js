/* ============================================================
   app.js – Landing page logic (Login + Register)
   Backend base URL comes from window.INTERVIEW_API_BASE when deployed separately.
   ============================================================ */

const API_BASE = window.INTERVIEW_API_BASE || '';

/* ── Tab switching ── */
function switchTab(tab) {
  ['login', 'register'].forEach(t => {
    document.getElementById(`tab-${t}`).classList.toggle('active', t === tab);
    document.getElementById(`tab-${t}`).setAttribute('aria-selected', t === tab);
    document.getElementById(`panel-${t}`).classList.toggle('active', t === tab);
  });
}

/* ── Show / hide role-dependent fields ── */
document.getElementById('reg-role').addEventListener('change', function () {
  document.getElementById('skills-group').style.display =
    this.value === 'interviewer' ? 'flex' : 'none';
});

// Hide skills group initially until role is chosen
document.getElementById('skills-group').style.display = 'none';

/* ── Alert helper ── */
function showAlert(id, message, type = 'error') {
  const el = document.getElementById(id);
  el.textContent = message;
  el.className = `alert alert-${type} show`;
  setTimeout(() => el.classList.remove('show'), 5000);
}

/* ── Loading state helpers ── */
function setLoading(btn, loading) {
  if (loading) {
    btn.disabled = true;
    btn.dataset.originalText = btn.textContent;
    btn.innerHTML = '<span class="spinner"></span> Please wait…';
  } else {
    btn.disabled = false;
    btn.textContent = btn.dataset.originalText;
  }
}

/* ── Redirect to dashboard ── */
function goToDashboard() {
  window.location.href = 'pages/dashboard.html';
}

/* ── Check if already logged in ── */
window.addEventListener('DOMContentLoaded', () => {
  const user = getStoredUser();
  if (user && user.id) {
    goToDashboard();
  }
});

/* ── LocalStorage helpers ── */
function storeUser(user) {
  localStorage.setItem('ip_user', JSON.stringify(user));
}

function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('ip_user'));
  } catch {
    return null;
  }
}

function unwrapApiResponse(payload) {
  return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload;
}

async function readApiError(res, fallback) {
  try {
    const payload = await res.json();
    return payload.message || fallback;
  } catch {
    const text = await res.text();
    return text || fallback;
  }
}

/* ============================================================
   LOGIN
   POST /api/users/login  { email, password }
   Response: user object { id, name, email, role, skills, ... }
   ============================================================ */
document.getElementById('login-form').addEventListener('submit', async function (e) {
  e.preventDefault();

  const email    = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;
  const btn      = document.getElementById('login-submit');

  if (!email || !password) {
    showAlert('login-alert', 'Please enter your email and password.');
    return;
  }

  setLoading(btn, true);

  try {
    const res = await fetch(`${API_BASE}/api/users/login`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ email, password }),
    });

    if (res.ok) {
      const user = unwrapApiResponse(await res.json());
      storeUser(user);
      showAlert('login-alert', 'Login successful! Redirecting...', 'success');
      setTimeout(goToDashboard, 800);
    } else {
      showAlert('login-alert', await readApiError(res, 'Invalid credentials. Please try again.'));
    }
  } catch (err) {
    showAlert('login-alert', 'Could not connect to the server. Is the backend running?');
    console.error('Login error:', err);
  } finally {
    setLoading(btn, false);
  }
});

/* ============================================================
   REGISTER
   POST /api/users/register  { name, email, password, role, skills[] }
   Response: created user object
   ============================================================ */
document.getElementById('register-form').addEventListener('submit', async function (e) {
  e.preventDefault();

  const name     = document.getElementById('reg-name').value.trim();
  const email    = document.getElementById('reg-email').value.trim();
  const password = document.getElementById('reg-password').value;
  const role     = document.getElementById('reg-role').value;
  const skillsRaw = document.getElementById('reg-skills').value;
  const btn      = document.getElementById('register-submit');

  // Basic validation
  if (!name || !email || !password || !role) {
    showAlert('register-alert', 'Please fill in all required fields.');
    return;
  }
  if (password.length < 6) {
    showAlert('register-alert', 'Password must be at least 6 characters.');
    return;
  }

  const skills = skillsRaw
    ? skillsRaw.split(',').map(s => s.trim()).filter(Boolean)
    : [];

  const payload = { name, username: name, email, password, role, skills };

  setLoading(btn, true);

  try {
    const res = await fetch(`${API_BASE}/api/users/register`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify(payload),
    });

    if (res.ok) {
      const user = unwrapApiResponse(await res.json());
      storeUser(user);
      showAlert('register-alert', 'Account created! Redirecting...', 'success');
      setTimeout(goToDashboard, 800);
    } else {
      showAlert('register-alert', await readApiError(res, 'Registration failed. Please try again.'));
    }
  } catch (err) {
    showAlert('register-alert', 'Could not connect to the server. Is the backend running?');
    console.error('Register error:', err);
  } finally {
    setLoading(btn, false);
  }
});
