const state = {
  selection: null
};

const elements = {
  status: document.getElementById("status"),
  editorTitle: document.getElementById("editor-title"),
  editor: document.getElementById("manifest-editor"),
  editorMessages: document.getElementById("editor-messages"),
  runInput: document.getElementById("run-input"),
  runOutput: document.getElementById("run-output"),
  validateBtn: document.getElementById("validate-btn"),
  saveBtn: document.getElementById("save-btn"),
  runBtn: document.getElementById("run-btn"),
  agentsList: document.getElementById("agents-list"),
  toolsList: document.getElementById("tools-list"),
  pipelinesList: document.getElementById("pipelines-list")
};

init().catch((error) => {
  elements.status.textContent = "API unavailable";
  writeEditorMessage(error.message || String(error));
});

async function init() {
  await checkHealth();
  await loadRepositoryLists();
  bindActions();
}

async function checkHealth() {
  const res = await fetch("/api/health");
  if (!res.ok) {
    throw new Error("Cannot reach /api/health");
  }
  elements.status.textContent = "API online";
}

async function loadRepositoryLists() {
  await Promise.all([
    loadList("agents", elements.agentsList),
    loadList("tools", elements.toolsList),
    loadList("pipelines", elements.pipelinesList)
  ]);
}

async function loadList(kindPlural, container) {
  const res = await fetch(`/api/${kindPlural}`);
  const payload = await readJson(res);
  container.innerHTML = "";
  for (const name of payload.items || []) {
    const li = document.createElement("li");
    const button = document.createElement("button");
    button.textContent = name;
    button.addEventListener("click", () => selectArtifact(kindPlural, name, button));
    li.appendChild(button);
    container.appendChild(li);
  }
}

async function selectArtifact(kindPlural, name, button) {
  document.querySelectorAll("li button.active").forEach((el) => el.classList.remove("active"));
  button.classList.add("active");

  const apiType = singular(kindPlural);
  const res = await fetch(`/api/manifest/${apiType}/${encodeURIComponent(name)}`);
  if (!res.ok) {
    const payload = await readJson(res);
    throw new Error(payload.message || `Cannot read ${apiType}/${name}`);
  }
  const yaml = await res.text();

  state.selection = { kindPlural, apiType, name };
  elements.editor.value = yaml;
  elements.editorTitle.textContent = `${apiType}: ${name}`;
  elements.runOutput.textContent = "";
}

function bindActions() {
  elements.validateBtn.addEventListener("click", validateCurrent);
  elements.saveBtn.addEventListener("click", saveCurrent);
  elements.runBtn.addEventListener("click", runCurrent);
}

async function validateCurrent() {
  const selection = ensureSelection();
  if (!selection) return;

  const res = await fetch(`/api/validate/${selection.apiType}`, {
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

  const res = await fetch(`/api/manifest/${selection.apiType}/${encodeURIComponent(selection.name)}`, {
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

  const res = await fetch(`/api/run/${selection.apiType}/${encodeURIComponent(selection.name)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  const payload = await readJson(res);
  elements.runOutput.textContent = JSON.stringify(payload, null, 2);
}

function ensureSelection() {
  if (state.selection) {
    return state.selection;
  }
  writeEditorMessage("Select an artifact first");
  return null;
}

function writeEditorMessage(text) {
  if (!elements.editorMessages) return;
  elements.editorMessages.textContent = text;
}

function singular(kindPlural) {
  if (kindPlural === "agents") return "agent";
  if (kindPlural === "tools") return "tool";
  return "pipeline";
}

async function readJson(response) {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}
