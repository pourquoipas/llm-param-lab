// LLM Param Lab — frontend shell (vanilla JS, no dependencies).
// Steps 12–14 fill in the per-tab render functions.

const state = {
  activeTab: 'models',
  models: [],
  suites: [],
  selectedSuiteId: null,
  suiteDraft: null,
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

// ---- Suites tab (Step 13) --------------------------------------------------
async function renderSuites() {
  const panel = document.getElementById('tab-suites');
  panel.innerHTML = '<div class="placeholder">Loading suites…</div>';
  if (!state.models.length) {
    try { state.models = await api('/api/models'); } catch (e) { /* ignore */ }
  }
  try { state.suites = await api('/api/suites'); }
  catch (e) { panel.innerHTML = '<div class="placeholder">Failed to load suites.</div>'; return; }

  panel.innerHTML = `
    <div class="row">
      <div class="card" style="flex:0 0 260px;">
        <div class="row" style="align-items:center; margin-bottom:12px;">
          <h3 style="margin:0; flex:1;">Suites</h3>
          <button class="btn-primary btn-sm" id="new-suite">New</button>
        </div>
        <ul class="suite-list" id="suite-list"></ul>
      </div>
      <div class="card" id="suite-editor" style="flex:1;"></div>
    </div>`;

  const list = panel.querySelector('#suite-list');
  state.suites.forEach((s) => {
    const li = document.createElement('li');
    li.className = 'suite-item' + (s.id === state.selectedSuiteId ? ' selected' : '');
    li.textContent = s.name;
    li.addEventListener('click', () => selectSuite(s.id));
    list.appendChild(li);
  });
  if (!state.suites.length) {
    const li = document.createElement('li');
    li.className = 'muted';
    li.textContent = 'No suites yet.';
    list.appendChild(li);
  }
  panel.querySelector('#new-suite').addEventListener('click', newSuite);
  renderSuiteEditor();
}

async function selectSuite(id) {
  state.selectedSuiteId = id;
  try { state.suiteDraft = await api('/api/suites/' + id); }
  catch (e) { return; }
  renderSuites();
}

function newSuite() {
  state.selectedSuiteId = null;
  state.suiteDraft = {
    id: null, name: '', description: '', expectedOutput: '',
    expectedOutputMode: 'NONE', judgeModelId: null, judgePrompt: '',
    testCases: [], paramSweeps: [],
  };
  renderSuites();
}

function renderSuiteEditor() {
  const editor = document.getElementById('suite-editor');
  const d = state.suiteDraft;
  if (!d) {
    editor.innerHTML = '<div class="placeholder">Select a suite or create a new one.</div>';
    return;
  }
  const isNew = d.id == null;
  const modes = ['EXACT', 'CONTAINS', 'REGEX', 'NONE'];
  const modelOptions = state.models.map((m) =>
    `<option value="${m.id}" ${m.id === d.judgeModelId ? 'selected' : ''}>${esc(m.name)}</option>`).join('');

  editor.innerHTML = `
    <h3>${isNew ? 'New Suite' : 'Suite #' + d.id}</h3>
    <label>Name</label><input id="s-name" value="${esc(d.name)}">
    <label>Description</label><input id="s-desc" value="${esc(d.description)}">
    <label>Expected Output</label><textarea id="s-expected">${esc(d.expectedOutput)}</textarea>
    <label>Expected Output Mode</label>
    <select id="s-mode">${modes.map((m) => `<option ${m === d.expectedOutputMode ? 'selected' : ''}>${m}</option>`).join('')}</select>
    <label>Judge Model</label>
    <select id="s-judge"><option value="">— none —</option>${modelOptions}</select>
    <label>Judge Prompt</label><textarea id="s-judgeprompt">${esc(d.judgePrompt)}</textarea>

    <div class="section-title">Test Cases</div>
    <div id="tc-list"></div>
    <button class="btn-sm" id="add-tc">+ Add Test Case</button>

    <div class="section-title">Param Sweeps</div>
    <div id="sweep-list"></div>
    <button class="btn-sm" id="add-sweep">+ Add Sweep</button>

    <div class="row mt" style="gap:8px;">
      <button class="btn-primary" id="save-suite">Save Suite</button>
      ${isNew ? '' : '<button id="run-suite">Run Suite</button>'}
      ${isNew ? '' : '<button class="btn-danger" id="delete-suite">Delete Suite</button>'}
    </div>`;

  const bind = (id, key) => {
    const el = editor.querySelector('#' + id);
    el.addEventListener('input', () => { d[key] = el.value; });
  };
  bind('s-name', 'name');
  bind('s-desc', 'description');
  bind('s-expected', 'expectedOutput');
  const mode = editor.querySelector('#s-mode');
  mode.addEventListener('change', () => { d.expectedOutputMode = mode.value; });
  const judge = editor.querySelector('#s-judge');
  judge.addEventListener('change', () => { d.judgeModelId = judge.value ? Number(judge.value) : null; });
  const judgePrompt = editor.querySelector('#s-judgeprompt');
  judgePrompt.addEventListener('input', () => { d.judgePrompt = judgePrompt.value; });

  editor.querySelector('#add-tc').addEventListener('click', () => {
    d.testCases.push({ name: '', systemPrompt: '', userPrompt: '', sortOrder: d.testCases.length });
    renderTestCases();
  });
  editor.querySelector('#add-sweep').addEventListener('click', () => {
    d.paramSweeps.push({ paramName: '', values: '[]' });
    renderSweeps();
  });
  editor.querySelector('#save-suite').addEventListener('click', saveSuite);
  if (!isNew) {
    editor.querySelector('#run-suite').addEventListener('click', runSuite);
    editor.querySelector('#delete-suite').addEventListener('click', deleteSuite);
  }

  renderTestCases();
  renderSweeps();
}

function renderTestCases() {
  const d = state.suiteDraft;
  const list = document.getElementById('tc-list');
  if (!list) return;
  list.innerHTML = '';
  d.testCases.forEach((tc, i) => {
    const row = document.createElement('div');
    row.className = 'sub-row';
    row.innerHTML = `
      <div class="sub-grid">
        <label>Name</label><input class="tc-name" value="${esc(tc.name)}">
        <label>System Prompt</label><textarea class="tc-sys" placeholder="System prompt for this test case (optional)">${esc(tc.systemPrompt)}</textarea>
        <label>User Prompt</label><textarea class="tc-user">${esc(tc.userPrompt)}</textarea>
        <label>Sort Order</label><input class="tc-order" type="number" value="${tc.sortOrder}">
      </div>
      <button class="btn-sm btn-danger tc-del">Delete</button>`;
    row.querySelector('.tc-name').addEventListener('input', (e) => { tc.name = e.target.value; });
    row.querySelector('.tc-sys').addEventListener('input', (e) => { tc.systemPrompt = e.target.value; });
    row.querySelector('.tc-user').addEventListener('input', (e) => { tc.userPrompt = e.target.value; });
    row.querySelector('.tc-order').addEventListener('input', (e) => { tc.sortOrder = Number(e.target.value) || 0; });
    row.querySelector('.tc-del').addEventListener('click', () => { d.testCases.splice(i, 1); renderTestCases(); });
    list.appendChild(row);
  });
}

function renderSweeps() {
  const d = state.suiteDraft;
  const list = document.getElementById('sweep-list');
  if (!list) return;
  list.innerHTML = '';
  d.paramSweeps.forEach((sw, i) => {
    const row = document.createElement('div');
    row.className = 'sub-row';
    row.innerHTML = `
      <div class="sub-grid">
        <label>Param Name</label><input class="sw-name" value="${esc(sw.paramName)}">
        <label>Values (comma-separated)</label><input class="sw-values" value="${esc(valuesToInput(sw.values))}">
      </div>
      <button class="btn-sm btn-danger sw-del">Delete</button>`;
    row.querySelector('.sw-name').addEventListener('input', (e) => { sw.paramName = e.target.value; });
    row.querySelector('.sw-values').addEventListener('input', (e) => { sw.values = inputToValues(e.target.value); });
    row.querySelector('.sw-del').addEventListener('click', () => { d.paramSweeps.splice(i, 1); renderSweeps(); });
    list.appendChild(row);
  });
}

function valuesToInput(valuesJson) {
  if (!valuesJson) return '';
  try {
    const arr = JSON.parse(valuesJson);
    return Array.isArray(arr) ? arr.join(', ') : valuesJson;
  } catch (e) { return valuesJson; }
}

function inputToValues(input) {
  const parts = input.split(',').map((s) => s.trim()).filter(Boolean);
  return '[' + parts.map((p) => {
    const n = Number(p);
    return Number.isFinite(n) ? String(n) : JSON.stringify(p);
  }).join(', ') + ']';
}

async function saveSuite() {
  const d = state.suiteDraft;
  if (!d.name) { toast('Suite name is required', 'error'); return; }
  const body = {
    name: d.name,
    description: d.description,
    expectedOutput: d.expectedOutput || null,
    expectedOutputMode: d.expectedOutputMode,
    judgeModelId: d.judgeModelId,
    judgePrompt: d.judgePrompt || null,
    testCases: d.testCases,
    paramSweeps: d.paramSweeps,
  };
  const isNew = d.id == null;
  try {
    const saved = isNew
      ? await api('/api/suites', { method: 'POST', body })
      : await api('/api/suites/' + d.id, { method: 'PUT', body });
    state.suiteDraft = saved;
    state.selectedSuiteId = saved.id;
    toast(isNew ? 'Suite created' : 'Suite saved', 'success');
    renderSuites();
  } catch (e) { /* toast already shown */ }
}

async function runSuite() {
  const d = state.suiteDraft;
  const btn = document.getElementById('run-suite');
  btn.disabled = true;
  showSpinner();
  try {
    const results = await api('/api/suites/' + d.id + '/run', { method: 'POST' });
    toast('Run complete: ' + results.length + ' results', 'success');
    state.results = results;
    switchTab('results');
  } catch (e) { /* toast already shown */ }
  finally {
    hideSpinner();
    btn.disabled = false;
  }
}

async function deleteSuite() {
  const d = state.suiteDraft;
  if (!confirm('Delete suite "' + d.name + '"?')) return;
  try {
    await api('/api/suites/' + d.id, { method: 'DELETE' });
    toast('Suite deleted', 'success');
    state.suiteDraft = null;
    state.selectedSuiteId = null;
    renderSuites();
  } catch (e) { /* toast already shown */ }
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
