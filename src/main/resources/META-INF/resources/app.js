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

// ---- Models tab (Step 12) --------------------------------------------------
async function renderModels() {
  const panel = document.getElementById('tab-models');
  panel.innerHTML = '<div class="placeholder">Loading models…</div>';
  try {
    state.models = await api('/api/models');
  } catch (e) {
    panel.innerHTML = '<div class="placeholder">Failed to load models.</div>';
    return;
  }
  const rows = state.models.map((m) => `
    <tr>
      <td>${esc(m.name)}</td>
      <td>${esc(m.provider)}</td>
      <td>${esc(m.baseUrl)}</td>
      <td>${esc(m.modelName)}</td>
      <td>${m.isActive ? '<span class="badge badge-active">ACTIVE</span>' : ''}</td>
      <td class="actions">
        <button class="btn-sm" data-act="edit" data-id="${m.id}">Edit</button>
        <button class="btn-sm" data-act="activate" data-id="${m.id}" ${m.isActive ? 'disabled' : ''}>Activate</button>
        <button class="btn-sm btn-danger" data-act="delete" data-id="${m.id}">Delete</button>
      </td>
    </tr>`).join('');
  panel.innerHTML = `
    <div class="card">
      <div class="row" style="align-items:center; margin-bottom:12px;">
        <h3 style="margin:0; flex:1;">Models</h3>
        <button class="btn-primary" id="add-model">Add Model</button>
      </div>
      <table>
        <thead><tr><th>Name</th><th>Provider</th><th>Base URL</th><th>Model</th><th>Active</th><th>Actions</th></tr></thead>
        <tbody id="models-tbody">${rows}</tbody>
      </table>
    </div>`;
  panel.querySelector('#add-model').addEventListener('click', () => openModelModal());
  panel.querySelectorAll('[data-act]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = Number(btn.dataset.id);
      const act = btn.dataset.act;
      if (act === 'edit') openModelModal(state.models.find((m) => m.id === id));
      else if (act === 'activate') activateModel(id);
      else if (act === 'delete') deleteModel(id);
    });
  });
}

function openModelModal(model) {
  const isEdit = !!model;
  const m = model || { name: '', provider: 'OLLAMA', baseUrl: '', apiKey: '', modelName: '' };
  const providers = ['OLLAMA', 'OPENAI_COMPATIBLE'];
  const { modal, close } = openModal(isEdit ? 'Edit Model' : 'Add Model', `
    <label>Name</label><input id="f-name" value="${esc(m.name)}">
    <label>Provider</label>
    <select id="f-provider">${providers.map((p) => `<option ${p === m.provider ? 'selected' : ''}>${p}</option>`).join('')}</select>
    <label>Base URL</label><input id="f-baseurl" value="${esc(m.baseUrl)}">
    <label>API Key (optional)</label><input id="f-apikey" value="${esc(m.apiKey)}">
    <label>Model Name</label><input id="f-modelname" value="${esc(m.modelName)}">
  `);
  const footer = modal.querySelector('.modal-footer');
  const save = document.createElement('button');
  save.className = 'btn-primary';
  save.textContent = 'Save';
  const cancel = document.createElement('button');
  cancel.textContent = 'Cancel';
  cancel.addEventListener('click', close);
  save.addEventListener('click', async () => {
    const body = {
      name: modal.querySelector('#f-name').value.trim(),
      provider: modal.querySelector('#f-provider').value,
      baseUrl: modal.querySelector('#f-baseurl').value.trim(),
      apiKey: modal.querySelector('#f-apikey').value.trim() || null,
      modelName: modal.querySelector('#f-modelname').value.trim(),
    };
    if (!body.name || !body.baseUrl || !body.modelName) { toast('Name, Base URL and Model are required', 'error'); return; }
    save.disabled = true;
    try {
      if (isEdit) await api('/api/models/' + model.id, { method: 'PUT', body });
      else await api('/api/models', { method: 'POST', body });
      close();
      toast(isEdit ? 'Model updated' : 'Model created', 'success');
      await renderActiveModel();
      renderModels();
    } catch (e) { save.disabled = false; }
  });
  footer.append(cancel, save);
}

async function activateModel(id) {
  try {
    await api('/api/models/' + id + '/activate', { method: 'POST' });
    toast('Model activated', 'success');
    await renderActiveModel();
    renderModels();
  } catch (e) { /* toast already shown */ }
}

async function deleteModel(id) {
  const m = state.models.find((x) => x.id === id);
  if (!confirm('Delete model "' + m.name + '"?')) return;
  try {
    await api('/api/models/' + id, { method: 'DELETE' });
    toast('Model deleted', 'success');
    await renderActiveModel();
    renderModels();
  } catch (e) { /* toast already shown */ }
}

function esc(s) {
  return s == null ? '' : String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
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
