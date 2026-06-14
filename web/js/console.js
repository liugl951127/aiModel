/* AI Agent Platform — Trainer Console
 * Pure HTML+JS, no build step. Talks directly to backend services.
 * Configure base URLs in the header inputs.
 */
(() => {
  const $ = (id) => document.getElementById(id);
  const api = (base, path, opts) => {
    const url = base.replace(/\/$/, '') + path;
    return fetch(url, { headers: { 'Content-Type': 'application/json' }, ...opts })
      .then(r => r.json().then(j => ({ status: r.status, body: j })));
  };
  const trainerBase = () => $('trainerBase').value;
  const workflowBase = () => $('workflowBase').value;
  const knowledgeBase = () => $('knowledgeBase').value;

  /* ---------- tabs ---------- */
  document.querySelectorAll('nav.tabs button').forEach(b => {
    b.addEventListener('click', () => {
      document.querySelectorAll('nav.tabs button').forEach(x => x.classList.remove('active'));
      document.querySelectorAll('.panel').forEach(x => x.classList.remove('active'));
      b.classList.add('active');
      document.querySelector(`.panel[data-tab="${b.dataset.tab}"]`).classList.add('active');
    });
  });

  /* ---------- build guard config from form ---------- */
  const buildGuard = () => ({
    topK: parseInt($('g_topK').value, 10),
    topP: parseFloat($('g_topP').value),
    temperature: parseFloat($('g_temperature').value),
    repetitionPenalty: parseFloat($('g_rep').value),
    maxConsecutiveUnknowns: parseInt($('g_maxUnk').value, 10),
    knowledgeGrounding: $('g_kg').checked,
    knowledgeKbId: $('g_kbId').value ? parseInt($('g_kbId').value, 10) : null,
    knowledgeTopK: parseInt($('g_kgK').value, 10),
    knowledgeDocIds: $('g_docIds').value
      ? $('g_docIds').value.split(',').map(s => parseInt(s.trim(), 10)).filter(Boolean)
      : null,
    minConfidence: parseFloat($('g_conf').value),
    maxAnswerTokens: parseInt($('g_maxTok').value, 10),
    factCheck: $('g_fact').checked,
    minFactOverlap: parseFloat($('g_factMin').value)
  });
  const applyGuard = (g) => {
    $('g_topK').value = g.topK;
    $('g_topP').value = g.topP;
    $('g_temperature').value = g.temperature;
    $('g_rep').value = g.repetitionPenalty;
    $('g_maxUnk').value = g.maxConsecutiveUnknowns;
    $('g_kg').checked = g.knowledgeGrounding;
    $('g_kbId').value = g.knowledgeKbId || '';
    $('g_kgK').value = g.knowledgeTopK;
    $('g_docIds').value = (g.knowledgeDocIds || []).join(',');
    $('g_conf').value = g.minConfidence;
    $('g_maxTok').value = g.maxAnswerTokens;
    $('g_fact').checked = g.factCheck;
    $('g_factMin').value = g.minFactOverlap;
    $('guardDump').textContent = JSON.stringify(g, null, 2);
  };

  $('btnGuardApply').onclick = () => $('guardDump').textContent = JSON.stringify(buildGuard(), null, 2);
  $('btnGuardStrict').onclick = () => applyGuard({
    topK: 8, topP: 0.6, temperature: 0.2, repetitionPenalty: 1.4,
    maxConsecutiveUnknowns: 2, knowledgeGrounding: true,
    knowledgeKbId: null, knowledgeTopK: 3, knowledgeDocIds: null,
    minConfidence: 1.5, maxAnswerTokens: 256, factCheck: true, minFactOverlap: 0.35
  });
  $('btnGuardReset').onclick = () => applyGuard({
    topK: 40, topP: 0.9, temperature: 0.7, repetitionPenalty: 1.15,
    maxConsecutiveUnknowns: 4, knowledgeGrounding: false,
    knowledgeKbId: null, knowledgeTopK: 3, knowledgeDocIds: null,
    minConfidence: 0, maxAnswerTokens: 256, factCheck: false, minFactOverlap: 0.15
  });
  applyGuard({
    topK: 40, topP: 0.9, temperature: 0.7, repetitionPenalty: 1.15,
    maxConsecutiveUnknowns: 4, knowledgeGrounding: false,
    knowledgeKbId: null, knowledgeTopK: 3, knowledgeDocIds: null,
    minConfidence: 0, maxAnswerTokens: 256, factCheck: false, minFactOverlap: 0.15
  });

  $('btnPresetStrict').onclick = $('btnGuardStrict').onclick;

  /* ---------- submit training ---------- */
  $('btnSubmit').onclick = async () => {
    const body = {
      modelType: $('modelType').value,
      corpusPath: $('corpusPath').value,
      maxIters: parseInt($('maxIters').value, 10),
      batchSize: parseInt($('batchSize').value, 10),
      blockSize: parseInt($('blockSize').value, 10),
      nLayer: parseInt($('nLayer').value, 10),
      nHead: parseInt($('nHead').value, 10),
      nEmbd: parseInt($('nEmbd').value, 10),
      learningRate: parseFloat($('learningRate').value),
      knowledgeKbId: $('knowledgeKbId').value ? parseInt($('knowledgeKbId').value, 10) : null,
      knowledgeSeedTopics: $('knowledgeSeedTopics').value
        ? $('knowledgeSeedTopics').value.split(',').map(s => s.trim()).filter(Boolean)
        : null,
      guard: buildGuard()
    };
    const r = await api(trainerBase(), '/api/trainer/submit',
      { method: 'POST', body: JSON.stringify(body) });
    $('submitResult').textContent = JSON.stringify(r, null, 2);
    if (r.body && r.body.data && r.body.data.jobId) {
      $('pvJobId').value = r.body.data.jobId;
    }
  };

  /* ---------- SSE preview ---------- */
  let es = null;
  $('btnPvSubscribe').onclick = () => {
    if (es) { es.close(); es = null; }
    const jobId = $('pvJobId').value.trim();
    if (!jobId) return alert('jobId is required');
    $('pvStream').textContent = '';
    es = new EventSource(`${trainerBase()}/api/trainer/preview/${jobId}/subscribe`);
    const onAny = (label) => (ev) => appendPv(`${label} ${ev.data}`);
    es.addEventListener('preview:metric',  onAny('[metric]'));
    es.addEventListener('preview:token',   onAny('[token]'));
    es.addEventListener('preview:rejected',onAny('[reject]'));
    es.addEventListener('preview:done',    (ev) => { appendPv(`[done] ${ev.data}`); es.close(); es = null; });
    es.onerror = (e) => appendPv(`[error] ${JSON.stringify(e)}`);
  };
  $('btnPvRun').onclick = async () => {
    const jobId = $('pvJobId').value.trim();
    if (!jobId) return alert('jobId required');
    const prompt = $('pvPrompt').value;
    const r = await api(trainerBase(),
      `/api/trainer/preview/${jobId}/generate`,
      { method: 'POST', body: JSON.stringify({ prompt, maxTokens: parseInt($('pvMax').value, 10) }) });
    $('pvResult').textContent = JSON.stringify(r, null, 2);
  };
  const appendPv = (line) => {
    const el = $('pvStream');
    el.textContent += line + '\n';
    el.scrollTop = el.scrollHeight;
  };

  /* ---------- knowledge ---------- */
  $('btnKbList').onclick = async () => {
    const r = await api(knowledgeBase(), '/api/knowledge/base/list');
    $('kbList').textContent = JSON.stringify(r, null, 2);
  };
  $('btnQa').onclick = async () => {
    const body = {
      kbId: $('qaKbId').value ? parseInt($('qaKbId').value, 10) : null,
      topics: $('qaTopics').value.split(',').map(s => s.trim()).filter(Boolean)
    };
    const r = await api(trainerBase(), '/api/trainer/dataset/qa',
      { method: 'POST', body: JSON.stringify(body) });
    $('qaResult').textContent = JSON.stringify(r, null, 2);
  };

  /* ---------- workflow ---------- */
  $('btnWfTemplate').onclick = async () => {
    const r = await api(workflowBase(), '/api/workflow/templates/train-eval-deploy');
    $('wfSpec').value = JSON.stringify(r.body.data, null, 2);
  };
  $('btnWfRun').onclick = async () => {
    let spec;
    try { spec = JSON.parse($('wfSpec').value); } catch (e) { return alert('invalid JSON: ' + e.message); }
    const r = await api(workflowBase(), '/api/workflow/run',
      { method: 'POST', body: JSON.stringify(spec) });
    $('wfResult').textContent = JSON.stringify(r, null, 2);
    const runId = r.body && r.body.data;
    if (runId) {
      setTimeout(async () => {
        const r2 = await api(workflowBase(), `/api/workflow/run/${runId}`);
        $('wfResult').textContent += '\n\n' + JSON.stringify(r2, null, 2);
      }, 3000);
    }
  };

  /* ---------- jobs ---------- */
  $('btnJobsRefresh').onclick = async () => {
    const r = await api(trainerBase(), '/api/trainer/jobs');
    const tbody = document.querySelector('#jobsTable tbody');
    tbody.innerHTML = '';
    const data = (r.body && r.body.data) || {};
    for (const k of Object.keys(data)) {
      const j = data[k];
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${k}</td>
        <td class="status-${j.status}">${j.status}</td>
        <td>${j.progress}</td>
        <td>${(j.finalLoss || 0).toFixed ? j.finalLoss.toFixed(4) : j.finalLoss}</td>
        <td>${j.bundleName || ''}</td>
        <td>${j.outputPath || ''}</td>
        <td>${j.error || ''}</td>
      `;
      tbody.appendChild(tr);
    }
  };
})();
