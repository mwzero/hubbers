# Analisi comparativa: Hubbers vs Manus vs OpenClaw vs Claude Code

_Data analisi: 2 aprile 2026._

## 1) Sintesi esecutiva

- **Hubbers** è un runtime Java "artifact-first": definisci agenti/tool/pipeline in YAML versionati su Git e li esegui via CLI/Web API. È ideale per chi vuole **controllo architetturale, tipizzazione e orchestrazione deterministica** in un proprio repository.
- **Claude Code** è un agente di coding in terminale/IDE, fortemente orientato alla produttività sviluppatore e all'ecosistema MCP/CI.
- **OpenClaw** è un assistente personale self-hosted multi-canale (messaggistica/voice), con orientamento "always-on" e uso operativo quotidiano.
- **Manus** è una piattaforma orientata a workflow applicativi con integrazioni (es. GitHub, MCP connectors) e collaborazione in task.

In breve:
- se vuoi un **framework runtime embeddabile e governabile in Git** → Hubbers;
- se vuoi un **copilot agentico per sviluppo software** → Claude Code;
- se vuoi un **assistente personale locale multi-canale** → OpenClaw;
- se vuoi un **ambiente workflow/productivity con sync GitHub e connettori** → Manus.

## 2) Cosa emerge dal codice di Hubbers

### Architettura
- Runtime centrato su `RuntimeFacade`, che espone esecuzione di agenti, tool e pipeline con validazione manifest prima del run.
- Artifact repository locale + scanner directory (`agents/`, `tools/`, `pipelines/`) per discovery Git-native.
- Pipeline eseguite in sequenza con `input_mapping` tra step e stato interno.

### Estendibilità
- Registro provider modello con implementazioni OpenAI + Ollama.
- Executor tool basato su driver per tipo (`http`, `docker`, `rss`, `lucene.*`, `browser.pinchtab`, `csv.*`, `file ops`, `shell exec`, `process manage`).
- API Web con endpoint per list/validate/save/run oltre alla CLI.

### Nota tecnica importante
- In `Bootstrap` viene istanziato anche `AgenticExecutor` (tool-calling + memory), ma il `RuntimeFacade` finale usa `AgentExecutor` classico: questo suggerisce che la parte "agentica" avanzata non è ancora fully wired nel percorso principale.

## 3) Confronto strutturato

| Criterio | Hubbers | Manus | OpenClaw | Claude Code |
|---|---|---|---|---|
| Posizionamento | Runtime framework Java per artifact YAML | Piattaforma workflow con connettori/MCP e collaborazione | Assistente personale self-hosted multi-canale | Agente di coding in terminale/IDE |
| Modalità d'uso | CLI + Web API, repo locale | Web product + integrazioni (GitHub sync, collab) | Gateway locale, canali chat/voice, CLI | CLI/IDE/GitHub, comandi NL |
| Unità di composizione | agent/tool/pipeline manifest | task/progetto con integrazioni | agent + skills + canali | sessione coding + tools/MCP |
| Governance & Git | Molto alta (Git-native per design) | Alta lato progetto (sync bidirezionale GitHub) | Variabile, dipende dal setup self-hosted | Alta nel ciclo dev, ma tool-centric |
| Estendibilità tool | Driver Java forti, schema validation | Connettori MCP/app integrations | Skills + integrazioni canale/dispositivo | MCP + plugin + automation CI |
| Target primario | Team platform/engineering | Team prodotto/ops | Power user self-hosted | Developer individual/team |

## 4) Gap/focus per portare Hubbers verso gli altri

1. **Vs Claude Code (DX coding)**
   - Migliorare UX per loop di coding (comandi ad alto livello tipo "fix, test, commit").
   - Potenziare integrazione IDE e workflow PR nativi.

2. **Vs OpenClaw (operatività always-on)**
   - Aggiungere canali realtime (chat connectors) e gestione identità/permessi per utenti finali.
   - Rafforzare runtime supervision/long-running tasks.

3. **Vs Manus (workflow produttivo business)**
   - Espandere catalogo connettori prebuilt e UI di orchestrazione collaborativa.
   - Introdurre collaborazione multi-utente su task/pipeline con controllo costi/quote.

## 5) Raccomandazione pratica

Se il tuo obiettivo è costruire un **"control plane agentico enterprise"**:
- mantieni Hubbers come nucleo (manifest + validazione + orchestrazione);
- aggiungi uno strato "experience":
  - **Developer experience** stile Claude Code,
  - **connectors/workflow** stile Manus,
  - **runtime always-on multi-canale** stile OpenClaw.

In questo modo preservi il vantaggio di Hubbers (governance, deterministic execution, portabilità Java) senza rinunciare alla velocità d'adozione dei framework più "productized".

## Fonti esterne consultate

- Claude Code overview/docs: https://code.claude.com/docs/en/overview
- Repository Claude Code: https://github.com/anthropics/claude-code
- Repository OpenClaw: https://github.com/openclaw/openclaw
- Sito/documentazione Manus:
  - https://manus.im/
  - https://manus.im/docs/website-builder/github-integration
  - https://manus.im/docs/integrations/mcp-connectors
