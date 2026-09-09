// LLM Param Lab — frontend shell (vanilla JS, no dependencies).
// Steps 12–14 fill in the per-tab render functions.

const state = {
  activeTab: 'models',
  models: [],
  suites: [],
  selectedSuiteId: null,
  results: [],
};

// ---- API wrapper -----------------------------------------------------------
async function api(path, options = {}) {
  const opts = { ...options };
  if (opts.body && typeof opts.body === 'object') {
    opts.headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
    opts.body = JSON.stringify(opts.body);
  }
  const res = await fetch(path, opts);
  if (!res.ok) {
    let msg = res.status + ' ' + res.statusText;
    try {
      const err = await res.json();
      if (err && err.error) msg = err.error;
    } catch (e) { /* non-JSON error body */ }
    toast(msg, 'error');
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// ---- Toast -----------------------------------------------------------------
function toast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  el.textContent = message;
  container.appendChild(el);
  setTimeout(() => {
    el.classList.add('hide');
    setTimeout(() => el.remove(), 300);
  }, 3000);
}

// ---- Spinner ---------------------------------------------------------------
function showSpinner() { document.getElementById('spinner-overlay').classList.remove('hidden'); }
function hideSpinner() { document.getElementById('spinner-overlay').classList.add('hidden'); }

// ---- Modal -----------------------------------------------------------------
function openModal(title, bodyHtml) {
  const tpl = document.getElementById('modal-template');
  const clone = tpl.content.cloneNode(true);
  clone.querySelector('.modal-title').textContent = title;
  clone.querySelector('.modal-body').innerHTML = bodyHtml;
  const root = document.getElementById('modal-root');
  root.appendChild(clone);
  const backdrop = clone.querySelector('.modal-backdrop');
  const close = () => backdrop.remove();
  clone.querySelector('.modal-close').addEventListener('click', close);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(); });
  return { modal: clone.querySelector('.modal'), close };
}

// ---- Tab switching ---------------------------------------------------------
function switchTab(tab) {
  state.activeTab = tab;
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
  document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === 'tab-' + tab));
  if (tab === 'models') renderModels();
  else if (tab === 'suites') renderSuites();
  else if (tab === 'results') renderResults();
}

// ---- Top bar: active model indicator ---------------------------------------
async function renderActiveModel() {
  const nameEl = document.getElementById('active-model-name');
  const dot = document.getElementById('active-model-dot');
  try {
    state.models = await api('/api/models');
    const active = state.models.find((m) => m.isActive);
    if (active) {
      nameEl.textContent = active.name;
      dot.classList.add('on');
    } else {
      nameEl.textContent = 'no active model';
      dot.classList.remove('on');
    }
  } catch (e) {
    nameEl.textContent = 'no active model';
    dot.classList.remove('on');
  }
}

// ---- Render helpers (filled in Steps 12–14) --------------------------------
function renderModels() {
  document.getElementById('tab-models').innerHTML =
    '<div class="placeholder">Models tab — Step 12</div>';
}

function renderSuites() {
  document.getElementById('tab-suites').innerHTML =
    '<div class="placeholder">Suites tab — Step 13</div>';
}

function renderResults() {
  document.getElementById('tab-results').innerHTML =
    '<div class="placeholder">Results tab — Step 14</div>';
}

// ---- Init ------------------------------------------------------------------
function init() {
  document.querySelectorAll('.tab').forEach((btn) => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });
  renderActiveModel();
  switchTab('models');
}

document.addEventListener('DOMContentLoaded', init);
