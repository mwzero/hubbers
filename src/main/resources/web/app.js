const state = {
  selection: null,
  pipelineDraft: [],
  executions: [],
  selectedExecution: null
};

const elements = {
  status: document.getElementById("status"),
  repoTree: document.getElementById("repo-tree"),
  editorTitle: document.getElementById("editor-title"),
  editorSubtitle: document.getElementById("editor-subtitle"),
  editor: document.getElementById("manifest-editor"),
  editorMessages: document.getElementById("editor-messages"),
  runInput: document.getElementById("run-input"),
  runOutput: document.getElementById("run-output"),
  validateBtn: document.getElementById("validate-btn"),
  saveBtn: document.getElementById("save-btn"),
  runBtn: document.getElementById("run-btn"),
  tabEditor: document.getElementById("tab-editor"),
  tabPipeline: document.getElementById("tab-pipeline"),
  tabExecutions: document.getElementById("tab-executions"),
  editorView: document.getElementById("editor-view"),
  pipelineView: document.getElementById("pipeline-view"),
  executionsView: document.getElementById("executions-view"),
  pipelineCanvas: document.getElementById("pipeline-canvas"),
  pipelineAddStep: document.getElementById("pipeline-add-step"),
  pipelineSync: document.getElementById("pipeline-sync"),
  executionsList: document.getElementById("executions-list"),
  executionsRefresh: document.getElementById("executions-refresh"),
  executionsFilterStatus: document.getElementById("executions-filter-status"),
  executionsFilterType: document.getElementById("executions-filter-type"),
  executionDetail: document.getElementById("execution-detail"),
  executionBack: document.getElementById("execution-back"),
  executionIdTitle: document.getElementById("execution-id-title"),
  executionDetailContent: document.getElementById("execution-detail-content")
};

init().catch((error) => {
  elements.status.textContent = "API unavailable";
  writeEditorMessage(error.message || String(error));
});

async function init() {
  bindActions();
  await checkHealth();
  const repo = await fetchRepoModel();
  renderRepoTree(repo);
}

function bindActions() {
  elements.validateBtn.addEventListener("click", validateCurrent);
  elements.saveBtn.addEventListener("click", saveCurrent);
  elements.runBtn.addEventListener("click", runCurrent);
  elements.tabEditor.addEventListener("click", () => setActiveTab("editor"));
  elements.tabPipeline.addEventListener("click", () => setActiveTab("pipeline"));
  elements.tabExecutions.addEventListener("click", () => { setActiveTab("executions"); loadExecutions(); });
  elements.pipelineAddStep.addEventListener("click", addPipelineStep);
  elements.pipelineSync.addEventListener("click", syncPipelineDesignerToYaml);
  elements.executionsRefresh.addEventListener("click", loadExecutions);
  elements.executionsFilterStatus.addEventListener("change", filterExecutions);
  elements.executionsFilterType.addEventListener("change", filterExecutions);
  elements.executionBack.addEventListener("click", () => showExecutionsList());
}

async function checkHealth() {
  const res = await fetch("/api/health");
  if (!res.ok) throw new Error("Cannot reach /api/health");
  elements.status.textContent = "API online";
}

async function fetchRepoModel() {
  const [agents, tools, pipelines] = await Promise.all([
    fetchList("agents"),
    fetchList("tools"),
    fetchList("pipelines")
  ]);

  return {
    repo: {
      agents: agents.map((name) => ({
        label: name,
        type: "agent",
        path: `repo/agents/${name}/agent.yaml`,
        name
      })),
      tools: tools.map((name) => ({
        label: name,
        type: "tool",
        path: `repo/tools/${name}/tool.yaml`,
        name
      })),
      pipelines: pipelines.map((name) => ({
        label: name,
        type: "pipeline",
        path: `repo/pipelines/${name}/pipeline.yaml`,
        name
      }))
    }
  };
}

async function fetchList(kindPlural) {
  const res = await fetch(`/api/${kindPlural}`);
  const payload = await readJson(res);
  return payload.items || [];
}

function renderRepoTree(model) {
  elements.repoTree.innerHTML = "";
  const root = document.createElement("div");
  root.className = "tree-node folder";
  root.textContent = "repo";

  const children = document.createElement("div");
  children.className = "tree-children";
  ["agents", "tools", "pipelines"].forEach((bucket) => {
    const folder = document.createElement("div");
    folder.className = "tree-node folder";
    folder.textContent = bucket;
    children.appendChild(folder);

    const subChildren = document.createElement("div");
    subChildren.className = "tree-children";

    for (const item of model.repo[bucket]) {
      const fileNode = document.createElement("div");
      fileNode.className = "tree-node file";
      fileNode.textContent = `${item.label}/` + item.path.split("/").pop();
      fileNode.dataset.type = item.type;
      fileNode.dataset.name = item.name;
      fileNode.title = item.path;
      fileNode.addEventListener("click", () => selectArtifact(item, fileNode));
      subChildren.appendChild(fileNode);
    }
    children.appendChild(subChildren);
  });

  elements.repoTree.appendChild(root);
  elements.repoTree.appendChild(children);
}

async function selectArtifact(item, node) {
  document.querySelectorAll(".tree-node.file.active").forEach((el) => el.classList.remove("active"));
  node.classList.add("active");

  const res = await fetch(`/api/manifest/${item.type}/${encodeURIComponent(item.name)}`);
  if (!res.ok) {
    const payload = await readJson(res);
    writeEditorMessage(payload.message || `Cannot read ${item.type}/${item.name}`);
    return;
  }

  state.selection = item;
  const yaml = await res.text();
  elements.editor.value = yaml;
  elements.editorTitle.textContent = item.path;
  elements.editorSubtitle.textContent = `${item.type.toUpperCase()} artifact: ${item.name}`;
  elements.runOutput.textContent = "";

  if (item.type === "pipeline") {
    state.pipelineDraft = parsePipelineSteps(yaml);
    renderPipelineDesigner();
    elements.tabPipeline.disabled = false;
  } else {
    state.pipelineDraft = [];
    renderPipelineDesigner();
    elements.tabPipeline.disabled = true;
    setActiveTab("editor");
  }
}

function setActiveTab(tab) {
  const isEditor = tab === "editor";
  const isPipeline = tab === "pipeline";
  const isExecutions = tab === "executions";
  
  elements.tabEditor.classList.toggle("active", isEditor);
  elements.tabPipeline.classList.toggle("active", isPipeline);
  elements.tabExecutions.classList.toggle("active", isExecutions);
  
  elements.editorView.classList.toggle("active", isEditor);
  elements.pipelineView.classList.toggle("active", isPipeline);
  elements.executionsView.classList.toggle("active", isExecutions);
}

function renderPipelineDesigner() {
  elements.pipelineCanvas.innerHTML = "";
  if (!state.selection || state.selection.type !== "pipeline") {
    elements.pipelineCanvas.innerHTML = '<div class="pipeline-empty">Select a pipeline to use the visual composer.</div>';
    return;
  }

  if (!state.pipelineDraft.length) {
    elements.pipelineCanvas.innerHTML = '<div class="pipeline-empty">No pipeline steps found. Add a step to start composing.</div>';
    return;
  }

  state.pipelineDraft.forEach((step, index) => {
    const card = document.createElement("div");
    card.className = "pipeline-step";

    card.innerHTML = `
      <div class="pipeline-step-header">
        <strong>Step ${index + 1}</strong>
        <button type="button" data-action="remove" data-index="${index}">Remove</button>
      </div>
      <input data-field="id" data-index="${index}" value="${escapeHtml(step.id || "")}" placeholder="Step ID (ex. fetch)" />
      <input data-field="target" data-index="${index}" value="${escapeHtml(step.target || "")}" placeholder="tool/agent/pipeline value" />
      <input data-field="targetType" data-index="${index}" value="${escapeHtml(step.targetType || "tool")}" placeholder="target type: tool | agent | pipeline" />
    `;
    elements.pipelineCanvas.appendChild(card);
  });

  elements.pipelineCanvas.querySelectorAll("input").forEach((input) => {
    input.addEventListener("input", (event) => {
      const idx = Number(event.target.dataset.index);
      const field = event.target.dataset.field;
      state.pipelineDraft[idx][field] = event.target.value;
    });
  });

  elements.pipelineCanvas.querySelectorAll("button[data-action='remove']").forEach((btn) => {
    btn.addEventListener("click", () => {
      const idx = Number(btn.dataset.index);
      state.pipelineDraft.splice(idx, 1);
      renderPipelineDesigner();
    });
  });
}

function addPipelineStep() {
  if (!state.selection || state.selection.type !== "pipeline") {
    writeEditorMessage("Open a pipeline artifact before adding visual steps.");
    return;
  }

  state.pipelineDraft.push({
    id: `step_${state.pipelineDraft.length + 1}`,
    targetType: "tool",
    target: ""
  });
  renderPipelineDesigner();
}

function syncPipelineDesignerToYaml() {
  if (!state.selection || state.selection.type !== "pipeline") {
    writeEditorMessage("Visual composer is available only for pipeline artifacts.");
    return;
  }

  const currentYaml = elements.editor.value;
  const updatedStepsBlock = buildStepsYaml(state.pipelineDraft);
  elements.editor.value = replaceOrAppendStepsBlock(currentYaml, updatedStepsBlock);
  writeEditorMessage("Pipeline visual changes applied to YAML editor.");
}

function parsePipelineSteps(yaml) {
  const lines = yaml.split("\n");
  const steps = [];
  let inSteps = false;
  let current = null;

  for (const rawLine of lines) {
    const line = rawLine.replace(/\t/g, "  ");

    if (/^steps:\s*$/.test(line.trim())) {
      inSteps = true;
      current = null;
      continue;
    }

    if (inSteps && /^[a-zA-Z_]+:\s*$/.test(line.trim()) && !line.startsWith("  -")) {
      break;
    }

    if (!inSteps) continue;

    const stepStart = line.match(/^\s*-\s+id:\s*(.+)\s*$/);
    if (stepStart) {
      if (current) steps.push(current);
      current = { id: stepStart[1].trim(), targetType: "tool", target: "" };
      continue;
    }

    if (!current) continue;

    const toolMatch = line.match(/^\s+tool:\s*(.+)\s*$/);
    const agentMatch = line.match(/^\s+agent:\s*(.+)\s*$/);
    const pipelineMatch = line.match(/^\s+pipeline:\s*(.+)\s*$/);

    if (toolMatch) {
      current.targetType = "tool";
      current.target = toolMatch[1].trim();
    } else if (agentMatch) {
      current.targetType = "agent";
      current.target = agentMatch[1].trim();
    } else if (pipelineMatch) {
      current.targetType = "pipeline";
      current.target = pipelineMatch[1].trim();
    }
  }

  if (current) steps.push(current);
  return steps;
}

function buildStepsYaml(steps) {
  if (!steps.length) return "steps: []\n";

  const rows = ["steps:"];
  steps.forEach((step) => {
    const id = (step.id || "unnamed_step").trim();
    const targetType = ["tool", "agent", "pipeline"].includes((step.targetType || "").trim())
      ? step.targetType.trim()
      : "tool";
    const target = (step.target || "").trim();

    rows.push(`  - id: ${id}`);
    rows.push(`    ${targetType}: ${target}`);
  });
  return rows.join("\n") + "\n";
}

function replaceOrAppendStepsBlock(yaml, stepsBlock) {
  const marker = /(^|\n)steps:\s*[\s\S]*?(?=\n[a-zA-Z_]+:\s*\n?|$)/m;
  if (marker.test(yaml)) {
    return yaml.replace(marker, (match, prefix) => `${prefix}${stepsBlock.trimEnd()}\n`);
  }
  return `${yaml.trimEnd()}\n\n${stepsBlock}`;
}

async function validateCurrent() {
  const selection = ensureSelection();
  if (!selection) return;

  const res = await fetch(`/api/validate/${selection.type}`, {
    method: "POST",
    headers: { "Content-Type": "text/yaml" },
    body: elements.editor.value
  });
  const payload = await readJson(res);
  if (payload.valid) {
    writeEditorMessage("Validation OK");
    return;
  }
  writeEditorMessage(`Validation failed:\n${(payload.errors || []).join("\n")}`);
}

async function saveCurrent() {
  const selection = ensureSelection();
  if (!selection) return;

  const res = await fetch(`/api/manifest/${selection.type}/${encodeURIComponent(selection.name)}`, {
    method: "PUT",
    headers: { "Content-Type": "text/yaml" },
    body: elements.editor.value
  });
  const payload = await readJson(res);

  if (!res.ok) {
    writeEditorMessage(`Save failed:\n${(payload.errors || [payload.message || "unknown error"]).join("\n")}`);
    return;
  }
  writeEditorMessage("Saved");
}

async function runCurrent() {
  const selection = ensureSelection();
  if (!selection) return;

  let input = {};
  try {
    input = elements.runInput.value.trim() ? JSON.parse(elements.runInput.value) : {};
  } catch (error) {
    elements.runOutput.textContent = `Invalid input JSON: ${error.message}`;
    return;
  }

  const res = await fetch(`/api/run/${selection.type}/${encodeURIComponent(selection.name)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  
  const payload = await readJson(res);
  
  // Check if execution requires a form
  if (res.status === 202 && payload.requiresForm) {
    // Show form modal
    showFormModal(payload.formSessionId, payload.form);
    elements.runOutput.textContent = "Awaiting form submission...";
    return;
  }
  
  elements.runOutput.textContent = JSON.stringify(payload, null, 2);
}

// Form rendering and submission
function showFormModal(sessionId, formDef) {
  const modal = document.createElement("div");
  modal.className = "form-modal";
  modal.id = "form-modal";
  
  modal.innerHTML = `
    <div class="modal-overlay" onclick="closeFormModal()">
      <div class="modal-content form-modal-content" onclick="event.stopPropagation()">
        <div class="modal-header">
          <h4>${escapeHtml(formDef.title || 'Input Required')}</h4>
          <button onclick="closeFormModal()">✕</button>
        </div>
        <div class="modal-body">
          ${formDef.description ? `<p class="form-description">${escapeHtml(formDef.description)}</p>` : ''}
          <form id="dynamic-form" data-session-id="${sessionId}">
            ${renderFormFields(formDef.fields)}
            <div class="form-actions">
              <button type="submit" class="btn-primary">Submit</button>
              <button type="button" onclick="closeFormModal()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;
  
  document.body.appendChild(modal);
  
  // Bind form submission
  document.getElementById("dynamic-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    await submitForm(sessionId);
  });
}

function renderFormFields(fields) {
  if (!fields ||fields.length === 0) {
    return '<p class="form-empty">No fields defined</p>';
  }
  
  return fields.map(field => {
    const required = field.required ? 'required' : '';
    const placeholder = field.placeholder ? `placeholder="${escapeHtml(field.placeholder)}"` : '';
    const defaultVal = field.defaultValue !== undefined ? escapeHtml(String(field.defaultValue)) : '';
    
    switch (field.type) {
      case 'textarea':
        return `
          <div class="form-field">
            <label for="field-${field.name}">${escapeHtml(field.label || field.name)}</label>
            <textarea id="field-${field.name}" name="${field.name}" ${placeholder} ${required}>${defaultVal}</textarea>
          </div>
        `;
      
      case 'number':
      case 'slider':
        const min = field.min !== undefined ? `min="${field.min}"` : '';
        const max = field.max !== undefined ? `max="${field.max}"` : '';
        const step = field.step !== undefined ? `step="${field.step}"` : '';
        return `
          <div class="form-field">
            <label for="field-${field.name}">${escapeHtml(field.label || field.name)}</label>
            <input type="number" id="field-${field.name}" name="${field.name}" value="${defaultVal}" ${min} ${max} ${step} ${required} />
          </div>
        `;
      
      case 'checkbox':
        const checked = field.defaultValue ? 'checked' : '';
        return `
          <div class="form-field form-field-checkbox">
            <input type="checkbox" id="field-${field.name}" name="${field.name}" ${checked} />
            <label for="field-${field.name}">${escapeHtml(field.label || field.name)}</label>
          </div>
        `;
      
      case 'select':
        const options = (field.options || []).map(opt =>
          `<option value="${escapeHtml(String(opt.value))}"${opt.value === field.defaultValue ? ' selected' : ''}>${escapeHtml(opt.label)}</option>`
        ).join('');
        return `
          <div class="form-field">
            <label for="field-${field.name}">${escapeHtml(field.label || field.name)}</label>
            <select id="field-${field.name}" name="${field.name}" ${required}>
              <option value="">-- Select --</option>
              ${options}
            </select>
          </div>
        `;
      
      case 'text':
      default:
        return `
          <div class="form-field">
            <label for="field-${field.name}">${escapeHtml(field.label || field.name)}</label>
            <input type="text" id="field-${field.name}" name="${field.name}" value="${defaultVal}" ${placeholder} ${required} />
          </div>
        `;
    }
  }).join('');
}

async function submitForm(sessionId) {
  const form = document.getElementById("dynamic-form");
  const formData = new FormData(form);
  const data = {};
  
  // Convert FormData to object
  for (const [key, value] of formData.entries()) {
    const input = form.elements[key];
    if (input.type === 'checkbox') {
      data[key] = input.checked;
    } else if (input.type === 'number') {
      data[key] = parseFloat(value) || 0;
    } else {
      data[key] = value;
    }
  }
  
  try {
    const res = await fetch(`/api/forms/${sessionId}/submit`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data)
    });
    
    const payload = await readJson(res);
    elements.runOutput.textContent = JSON.stringify(payload, null, 2);
    closeFormModal();
  } catch (error) {
    alert(`Form submission failed: ${error.message}`);
  }
}

window.closeFormModal = function() {
  const modal = document.getElementById("form-modal");
  if (modal) {
    modal.remove();
  }
};

function ensureSelection() {
  if (state.selection) return state.selection;
  writeEditorMessage("Select an artifact first");
  return null;
}

function writeEditorMessage(text) {
  elements.editorMessages.textContent = text;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function readJson(response) {
  const text = await response.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

// Executions functionality
async function loadExecutions() {
  try {
    const res = await fetch("/api/executions");
    const payload = await readJson(res);
    state.executions = payload.items || [];
    filterExecutions();
  } catch (error) {
    elements.executionsList.innerHTML = `<div class="execution-error">Failed to load executions: ${error.message}</div>`;
  }
}

function filterExecutions() {
  const statusFilter = elements.executionsFilterStatus.value;
  const typeFilter = elements.executionsFilterType.value;
  
  const filtered = state.executions.filter(execution => {
    const matchesStatus = !statusFilter || execution.status === statusFilter;
    const matchesType = !typeFilter || execution.artifactType === typeFilter;
    return matchesStatus && matchesType;
  });
  
  renderExecutionsList(filtered);
}

function renderExecutionsList(executions) {
  if (!executions || executions.length === 0) {
    elements.executionsList.innerHTML = '<div class="executions-empty">No executions found. Run an artifact to see execution history.</div>';
    return;
  }
  
  elements.executionsList.innerHTML = "";
  
  executions.forEach(execution => {
    const card = document.createElement("div");
    card.className = `execution-card status-${execution.status.toLowerCase()}`;
    
    const duration = execution.endedAt 
      ? `${((execution.endedAt - execution.startedAt) / 1000).toFixed(2)}s`
      : 'Running...';
    
    const date = new Date(execution.startedAt).toLocaleString();
    
    card.innerHTML = `
      <div class="execution-card-header">
        <span class="execution-id">${escapeHtml(execution.executionId)}</span>
        <span class="execution-status badge-${execution.status.toLowerCase()}">${execution.status}</span>
      </div>
      <div class="execution-card-body">
        <div class="execution-info">
          <strong>${escapeHtml(execution.artifactType)}</strong>: ${escapeHtml(execution.artifactName)}
        </div>
        <div class="execution-meta">
          <span>${date}</span>
          <span>${duration}</span>
        </div>
      </div>
    `;
    
    card.addEventListener("click", () => showExecutionDetail(execution.executionId));
    elements.executionsList.appendChild(card);
  });
}

async function showExecutionDetail(executionId) {
  state.selectedExecution = executionId;
  elements.executionsList.style.display = "none";
  elements.executionDetail.style.display = "block";
  elements.executionIdTitle.textContent = executionId;
  
  // Load metadata by default
  showExecutionTab("metadata");
}

function showExecutionsList() {
  elements.executionsList.style.display = "block";
  elements.executionDetail.style.display = "none";
  state.selectedExecution = null;
}

async function showExecutionTab(tabName) {
  // Update tab buttons
  document.querySelectorAll(".detail-tab").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.tab === tabName);
  });
  
  const executionId = state.selectedExecution;
  if (!executionId) return;
  
  elements.executionDetailContent.innerHTML = '<div class="loading">Loading...</div>';
  
  try {
    let content = "";
    
    switch (tabName) {
      case "metadata":
        const metadataRes = await fetch(`/api/executions/${encodeURIComponent(executionId)}`);
        const metadata = await readJson(metadataRes);
        content = `<pre class="detail-json">${JSON.stringify(metadata, null, 2)}</pre>`;
        break;
        
      case "log":
        const logRes = await fetch(`/api/executions/${encodeURIComponent(executionId)}/log`);
        const log = await logRes.text();
        content = `<pre class="detail-log">${escapeHtml(log)}</pre>`;
        break;
        
      case "input":
        const inputRes = await fetch(`/api/executions/${encodeURIComponent(executionId)}/input`);
        const input = await readJson(inputRes);
        content = `<pre class="detail-json">${JSON.stringify(input, null, 2)}</pre>`;
        break;
        
      case "output":
        const outputRes = await fetch(`/api/executions/${encodeURIComponent(executionId)}/output`);
        const output = await readJson(outputRes);
        content = `<pre class="detail-json">${JSON.stringify(output, null, 2)}</pre>`;
        break;
        
      case "steps":
        const stepsRes = await fetch(`/api/executions/${encodeURIComponent(executionId)}/steps`);
        const stepsData = await readJson(stepsRes);
        const steps = stepsData.items || [];
        
        if (steps.length === 0) {
          content = '<div class="execution-detail-empty">No pipeline steps found for this execution.</div>';
        } else {
          content = '<div class="steps-list">';
          steps.forEach((step, index) => {
            content += `
              <div class="step-card">
                <div class="step-header">
                  <strong>Step ${index + 1}</strong>: ${escapeHtml(step.name)}
                </div>
                <div class="step-actions">
                  ${step.hasInput ? `<button onclick="showStepDetail('${executionId}', '${step.name}', 'input')">Input</button>` : ''}
                  ${step.hasOutput ? `<button onclick="showStepDetail('${executionId}', '${step.name}', 'output')">Output</button>` : ''}
                  <button onclick="showStepDetail('${executionId}', '${step.name}', 'log')">Log</button>
                </div>
              </div>
            `;
          });
          content += '</div>';
        }
        break;
    }
    
    elements.executionDetailContent.innerHTML = content;
  } catch (error) {
    elements.executionDetailContent.innerHTML = `<div class="execution-error">Failed to load ${tabName}: ${error.message}</div>`;
  }
}

// Global function for step detail buttons
window.showStepDetail = async function(executionId, stepName, type) {
  const container = document.createElement("div");
  container.className = "step-detail-modal";
  
  try {
    const res = await fetch(`/api/executions/${encodeURIComponent(executionId)}/steps/${encodeURIComponent(stepName)}/${type}`);
    let content = "";
    
    if (type === "log") {
      content = await res.text();
      container.innerHTML = `
        <div class="modal-overlay" onclick="this.parentElement.remove()">
          <div class="modal-content" onclick="event.stopPropagation()">
            <div class="modal-header">
              <h4>${stepName} - Log</h4>
              <button onclick="this.closest('.step-detail-modal').remove()">✕</button>
            </div>
            <pre class="detail-log">${escapeHtml(content)}</pre>
          </div>
        </div>
      `;
    } else {
      const data = await readJson(res);
      container.innerHTML = `
        <div class="modal-overlay" onclick="this.parentElement.remove()">
          <div class="modal-content" onclick="event.stopPropagation()">
            <div class="modal-header">
              <h4>${stepName} - ${type.toUpperCase()}</h4>
              <button onclick="this.closest('.step-detail-modal').remove()">✕</button>
            </div>
            <pre class="detail-json">${JSON.stringify(data, null, 2)}</pre>
          </div>
        </div>
      `;
    }
    
    document.body.appendChild(container);
  } catch (error) {
    alert(`Failed to load step ${type}: ${error.message}`);
  }
};

// Add tab click handlers
document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".detail-tab").forEach(btn => {
    btn.addEventListener("click", () => showExecutionTab(btn.dataset.tab));
  });
});
