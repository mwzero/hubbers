const state = {
  selection: null,
  pipelineDraft: []
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
  editorView: document.getElementById("editor-view"),
  pipelineView: document.getElementById("pipeline-view"),
  pipelineCanvas: document.getElementById("pipeline-canvas"),
  pipelineAddStep: document.getElementById("pipeline-add-step"),
  pipelineSync: document.getElementById("pipeline-sync")
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
  elements.pipelineAddStep.addEventListener("click", addPipelineStep);
  elements.pipelineSync.addEventListener("click", syncPipelineDesignerToYaml);
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
  elements.tabEditor.classList.toggle("active", isEditor);
  elements.tabPipeline.classList.toggle("active", !isEditor);
  elements.editorView.classList.toggle("active", isEditor);
  elements.pipelineView.classList.toggle("active", !isEditor);
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
  elements.runOutput.textContent = JSON.stringify(payload, null, 2);
}

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
