// LLM Param Lab — frontend shell (vanilla JS, no dependencies).
// Steps 12–14 fill in the per-tab render functions.

const state = {
  activeTab: 'models',
  models: [],
  suites: [],
  selectedSuiteId: null,
  suiteDraft: null,
  results: [],
  resultsSuiteId: null,
  resultsTestCaseId: null,
  summary: null,
};

// Chat-level params that can be swept (applied per call, not pre-set on the model).
// Must mirror TestSuiteService.VALID_PARAMS.
const SWEEP_PARAMS = [
  { name: 'temperature', label: 'temperature (0–2)' },
  { name: 'topP', label: 'topP (0–1)' },
  { name: 'topK', label: 'topK (int)' },
  { name: 'frequencyPenalty', label: 'frequencyPenalty (-2–2)' },
  { name: 'presencePenalty', label: 'presencePenalty (-2–2)' },
  { name: 'maxTokens', label: 'maxTokens (int)' },
];

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
  // Query everything BEFORE appendChild: appending a DocumentFragment moves its
  // children out, so the fragment is empty afterwards and querySelector returns null.
  const modal = clone.querySelector('.modal');
  const backdrop = clone.querySelector('.modal-backdrop');
  const close = () => backdrop.remove();
  modal.querySelector('.modal-title').textContent = title;
  modal.querySelector('.modal-body').innerHTML = bodyHtml;
  modal.querySelector('.modal-close').addEventListener('click', close);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(); });
  const root = document.getElementById('modal-root');
  root.appendChild(clone);
  return { modal, close };
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
    judgeTemperature: null, judgeTopP: null, judgeSeed: null,
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
    <div class="sub-grid" style="grid-template-columns:repeat(3,1fr);">
      <label>Judge Temperature</label><input id="s-judgetemp" type="number" step="0.05" value="${d.judgeTemperature ?? ''}" placeholder="default 0">
      <label>Judge Top-P</label><input id="s-judgetopp" type="number" step="0.05" value="${d.judgeTopP ?? ''}">
      <label>Judge Seed</label><input id="s-judgeseed" type="number" step="1" value="${d.judgeSeed ?? ''}">
    </div>

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
  const numBind = (id, key) => {
    const el = editor.querySelector('#' + id);
    el.addEventListener('input', () => { d[key] = el.value === '' ? null : Number(el.value); });
  };
  numBind('s-judgetemp', 'judgeTemperature');
  numBind('s-judgetopp', 'judgeTopP');
  numBind('s-judgeseed', 'judgeSeed');

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
    const options = SWEEP_PARAMS
      .map((p) => `<option value="${p.name}"${p.name === sw.paramName ? ' selected' : ''}>${p.label}</option>`)
      .join('');
    row.innerHTML = `
      <div class="sub-grid">
        <label>Param</label>
        <select class="sw-name"><option value="">— choose —</option>${options}</select>
        <label>Values (comma-separated)</label><input class="sw-values" value="${esc(valuesToInput(sw.values))}">
      </div>
      <button class="btn-sm btn-danger sw-del">Delete</button>`;
    row.querySelector('.sw-name').addEventListener('change', (e) => { sw.paramName = e.target.value; });
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
    judgeTemperature: d.judgeTemperature ?? null,
    judgeTopP: d.judgeTopP ?? null,
    judgeSeed: d.judgeSeed ?? null,
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
    state.resultsSuiteId = d.id;
    state.resultsTestCaseId = null;
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

// ---- Results tab (Step 14) -------------------------------------------------
function formatParams(paramsJson) {
  if (!paramsJson) return '—';
  try {
    const obj = JSON.parse(paramsJson);
    return Object.entries(obj).map(([k, v]) => k + '=' + v).join(', ');
  } catch (e) { return paramsJson; }
}

async function renderResults() {
  const panel = document.getElementById('tab-results');
  panel.innerHTML = '<div class="placeholder">Loading results…</div>';

  if (!state.suites.length) {
    try { state.suites = await api('/api/suites'); } catch (e) { /* ignore */ }
  }
  if (!state.suites.length) {
    panel.innerHTML = '<div class="placeholder">No suites yet. Create one in the Suites tab.</div>';
    return;
  }

  if (state.resultsSuiteId == null || !state.suites.some((s) => s.id === state.resultsSuiteId)) {
    state.resultsSuiteId = state.suites[0].id;
    state.resultsTestCaseId = null;
  }

  const suite = state.suites.find((s) => s.id === state.resultsSuiteId);
  const tcName = (id) => {
    const tc = (suite.testCases || []).find((t) => t.id === id);
    return tc ? tc.name : '#' + id;
  };

  let results, summary;
  try {
    [results, summary] = await Promise.all([
      api('/api/results?suiteId=' + state.resultsSuiteId),
      api('/api/results/summary?suiteId=' + state.resultsSuiteId),
    ]);
  } catch (e) {
    panel.innerHTML = '<div class="placeholder">Failed to load results.</div>';
    return;
  }
  state.results = results;
  state.summary = summary;

  const tcOptions = (suite.testCases || []).map((t) =>
    `<option value="${t.id}" ${state.resultsTestCaseId === t.id ? 'selected' : ''}>${esc(t.name)}</option>`).join('');

  panel.innerHTML = `
    <div class="card">
      <div class="row" style="align-items:center; margin-bottom:12px;">
        <h3 style="margin:0; flex:1;">Results</h3>
        <button class="btn-primary" id="run-all">Run All</button>
      </div>
      <div class="row" style="gap:12px; align-items:flex-end;">
        <div style="flex:1;">
          <label>Suite</label>
          <select id="res-suite">${state.suites.map((s) => `<option value="${s.id}" ${s.id === state.resultsSuiteId ? 'selected' : ''}>${esc(s.name)}</option>`).join('')}</select>
        </div>
        <div style="flex:1;">
          <label>Test Case (optional)</label>
          <select id="res-tc"><option value="">All test cases</option>${tcOptions}</select>
        </div>
      </div>
    </div>
    <div class="card" id="res-summary"></div>
    <div class="card">
      <table>
        <thead><tr><th>Test Case</th><th>Params</th><th>Score</th><th>Passed</th><th>Latency</th><th>Tokens In/Out</th><th>Eval Type</th><th>Date</th></tr></thead>
        <tbody id="res-tbody"></tbody>
      </table>
    </div>`;

  panel.querySelector('#res-suite').addEventListener('change', (e) => {
    state.resultsSuiteId = Number(e.target.value);
    state.resultsTestCaseId = null;
    renderResults();
  });
  panel.querySelector('#res-tc').addEventListener('change', (e) => {
    state.resultsTestCaseId = e.target.value ? Number(e.target.value) : null;
    renderResultsTable(tcName);
  });
  panel.querySelector('#run-all').addEventListener('click', runAll);

  renderSummaryCard();
  renderResultsTable(tcName);
}

function renderSummaryCard() {
  const el = document.getElementById('res-summary');
  const summary = state.summary;
  if (!summary || !summary.testCases || !summary.testCases.length) {
    el.innerHTML = '<div class="muted">No results yet for this suite. Run it to see the best parameter combination.</div>';
    return;
  }
  const rows = summary.testCases.map((tc) => {
    const best = tc.bestCombo;
    if (!best) return `<div class="sub-row"><span class="muted">${esc(tc.testCaseName)} — no data</span></div>`;
    const score = best.avgScore != null ? best.avgScore.toFixed(2) : '—';
    return `<div class="sub-row">
      <div class="sub-grid">
        <div style="font-weight:600; margin-bottom:4px;">${esc(tc.testCaseName)}</div>
        <div class="muted">Best: ${esc(formatParams(best.paramsJson))} — avg score <strong>${score}</strong></div>
      </div>
    </div>`;
  }).join('');
  el.innerHTML = `<h3>Best param combo</h3>${rows}`;
}

function renderResultsTable(tcName) {
  const tbody = document.getElementById('res-tbody');
  if (!tbody) return;
  let results = state.results || [];
  if (state.resultsTestCaseId != null) {
    results = results.filter((r) => r.testCaseId === state.resultsTestCaseId);
  }
  if (!results.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="muted" style="text-align:center; padding:24px;">No results yet. Run the suite to generate results.</td></tr>';
    return;
  }
  tbody.innerHTML = results.map((r) => {
    const scoreClass = r.score == null ? '' : (r.score >= 0.7 ? 'score-high' : r.score >= 0.4 ? 'score-mid' : 'score-low');
    const scoreText = r.score == null ? '—' : r.score.toFixed(2);
    return `
      <tr class="clickable" data-id="${r.id}">
        <td>${esc(tcName(r.testCaseId))}</td>
        <td>${esc(formatParams(r.paramsJson))}</td>
        <td><span class="badge badge-score ${scoreClass}">${scoreText}</span></td>
        <td>${r.passed ? '✓' : '✗'}</td>
        <td>${r.latencyMs != null ? r.latencyMs + ' ms' : '—'}</td>
        <td>${r.tokensIn != null ? r.tokensIn : '—'} / ${r.tokensOut != null ? r.tokensOut : '—'}</td>
        <td>${esc(r.evaluationType)}</td>
        <td>${r.createdAt ? new Date(r.createdAt).toLocaleString() : '—'}</td>
      </tr>
      <tr class="res-detail" data-detail-for="${r.id}" style="display:none;">
        <td colspan="8">
          <label>Raw Output</label>
          <pre>${esc(r.rawOutput || '(empty)')}</pre>
          <label>Score Reason</label>
          <pre>${esc(r.scoreReason || '(none)')}</pre>
        </td>
      </tr>`;
  }).join('');

  tbody.querySelectorAll('tr.clickable').forEach((tr) => {
    tr.addEventListener('click', () => {
      const detail = tbody.querySelector(`tr[data-detail-for="${tr.dataset.id}"]`);
      if (detail) detail.style.display = detail.style.display === 'none' ? '' : 'none';
    });
  });
}

async function runAll() {
  const btn = document.getElementById('run-all');
  btn.disabled = true;
  showSpinner();
  try {
    const results = await api('/api/run-all', { method: 'POST' });
    toast('Run complete: ' + results.length + ' results', 'success');
    try { state.suites = await api('/api/suites'); } catch (e) { /* ignore */ }
    renderResults();
  } catch (e) { /* toast already shown */ }
  finally {
    hideSpinner();
    btn.disabled = false;
  }
}

// ---- Admin actions (top bar) -----------------------------------------------
async function insertTestCase() {
  const btn = document.getElementById('btn-test-case');
  btn.disabled = true;
  showSpinner();
  try {
    await api('/api/admin/test-case', { method: 'POST' });
    toast('Test case inserted: "Agent smoke test"', 'success');
    await renderActiveModel();
    switchTab('suites');
  } catch (e) { /* toast already shown */ }
  finally {
    hideSpinner();
    btn.disabled = false;
  }
}

async function cleanDatabase() {
  if (!confirm('Clean the entire database? This deletes all models, suites, and results.')) return;
  const btn = document.getElementById('btn-clean-db');
  btn.disabled = true;
  showSpinner();
  try {
    await api('/api/admin/clean', { method: 'POST' });
    toast('Database cleaned', 'success');
    state.suiteDraft = null;
    state.selectedSuiteId = null;
    await renderActiveModel();
    switchTab('models');
  } catch (e) { /* toast already shown */ }
  finally {
    hideSpinner();
    btn.disabled = false;
  }
}

// ---- Init ------------------------------------------------------------------
function init() {
  document.querySelectorAll('.tab').forEach((btn) => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });
  document.getElementById('btn-test-case').addEventListener('click', insertTestCase);
  document.getElementById('btn-clean-db').addEventListener('click', cleanDatabase);
  renderActiveModel();
  switchTab('models');
}

document.addEventListener('DOMContentLoaded', init);
