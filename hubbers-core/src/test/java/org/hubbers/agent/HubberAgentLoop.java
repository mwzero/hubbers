package org.hubbers.agent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ciclo ReAct - Reason/Act
 *  ┌─────────────────────────────────────────┐
    │          Input dell'Utente              │
    └────────────────────┬────────────────────┘
                         ▼
             ┌──────────────────────┐
    ┌───────►│  THINK (Pensiero)    │
    │        └───────────┬──────────┘
    │                    ▼
    │        ┌──────────────────────┐
    │        │   ACT (Azione)       │
    │        └───────────┬──────────┘
    │                    ▼
    │        ┌──────────────────────┐
    │        │ OBSERVE (Osservazione)│
    │        └───────────┬──────────┘
    │                    ▼
    │        ┌──────────────────────┐
    │        │ REFLECT (Riflessione)│
    │        └───────────┬──────────┘
    │                    │
    └────────────────────┴────────────────────► Output Finale
 
Le 4 Fasi del Ciclo

1. Think (Pensiero / Pianificazione)
L'agente riceve la tua richiesta e consulta tre elementi: il contesto della chat attuale, 
le regole in SOUL.md e i dati storici in MEMORY.md. Genera quindi una stringa di pensiero interna in cui stabilisce la strategia.
Esempio: "L'utente vuole i dati finanziari di Apple. Devo prima cercare su internet il report più recente, poi estrarre la tabella e salvarla in un file CSV."

2. Act (Azione / Chiamata ai Tool)Il modello decide quale strumento (Tool Python o comando Shell) è necessario in quel preciso momento ed emette un comando strutturato 
(generalmente in formato JSON o tramite tag specifici).Esempio: Genera ed esegue la chiamata al tool web_search(query="Apple Q3 2025 financial report").

3. Observe (Osservazione del Risultato)L'agente si mette in "ascolto". Il framework esegue il codice Python del tool e reinietta il risultato dell'azione direttamente nel 
prompt dell'agente. L'IA riceve l'output grezzo dell'operazione.Esempio: Riceve il testo html o i dati testuali estratti dalla pagina web.

4. Reflect (Riflessione e Controllo degli Errori)L'agente valuta l'osservazione appena ricevuta. Si pone due domande: Il compito è terminato? Ci sono stati errori?
Se c'è un errore (es. errore 404 sul sito web o errore di sintassi nel terminale), l'agente non si ferma: usa l'errore come feedback, modifica il piano e ricomincia dal punto 1 
(Think) per correggersi.Se il compito richiede altri passaggi, seleziona il tool successivo.Se il compito è completato, esce dal ciclo e formula la risposta finale per l'utente.
 
*/
public class HubberAgentLoop {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL_NAME = "qwen3:4b"; 
    private static final int MAX_ITERATIONS = 5;

    private static final String SOUL_RESOURCE = "/org/hubbers/agent/soul.md";
    private static final String MEMORY_RESOURCE = "/org/hubbers/agent/memory.md";

    private final List<String> conversationHistory = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) {
        HubberAgentLoop agent = new HubberAgentLoop();
        agent.initSystemPrompt();

        System.out.println("🤖 Hubber Agent Loop Pronto (Modello: " + MODEL_NAME + ").");
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("\n👤 Utente: ");
            String userInput = scanner.nextLine();
            if (userInput.equalsIgnoreCase("exit")) break;

            agent.runLoop(userInput);
        }
        scanner.close();
    }

    /**
     * Assembla il prompt di sistema leggendo dinamicamente soul.md e memory.md.
     * Non crea più i file se non esistono.
     */
    private void initSystemPrompt() {
        String soulContent = "";
        String memoryContent = "";
        
        try (InputStream is = HubberAgentLoop.class.getResourceAsStream(SOUL_RESOURCE)) {
            if (is != null) {
                soulContent = new String(is.readAllBytes());
            } else {
                System.err.println("⚠️ Attenzione: Impossibile leggere " + SOUL_RESOURCE + ". Assicurati che esista in src/main/resources.");
                soulContent = "Tu sei Hubber, un agente autonomo.";
            }
        } catch (IOException e) {
            System.err.println("⚠️ Attenzione: Impossibile leggere " + SOUL_RESOURCE + ". Assicurati che esista in src/main/resources.");
            soulContent = "Tu sei Hubber, un agente autonomo.";
        }

        try (InputStream is = HubberAgentLoop.class.getResourceAsStream(MEMORY_RESOURCE)) {
            if (is != null) {
                memoryContent = new String(is.readAllBytes());
            } else {
                System.err.println("⚠️ Attenzione: Impossibile leggere " + MEMORY_RESOURCE + ". Assicurati che esista in src/main/resources.");
                memoryContent = "Nessun dato in memoria.";
            }
        } catch (IOException e) {
            System.err.println("⚠️ Attenzione: Impossibile leggere " + MEMORY_RESOURCE + ". Assicurati che esista in src/main/resources.");
            memoryContent = "Nessun dato in memoria.";
        }

        String systemPrompt = "=== REGOLE DI SISTEMA (DASHBOARD) ===\n" +
                soulContent + "\n\n" +
                "=== MEMORIA A LUNGO TERMINE ===\n" +
                memoryContent + "\n\n" +
                "=== TOOLS DISPONIBILI ===\n" +
                "1. <call:file_write filename=\"nome_file\">contenuto</call>\n" +
                "2. <call:file_read filename=\"nome_file\"></call>\n" +
                "3. <call:memory_append>testo da aggiungere alle note di memoria</call>\n\n" +
                "SINTASSI OBBLIGATORIA DI RISPOSTA:\n" +
                "- Inizia SEMPRE la risposta inserendo i tuoi pensieri dentro i tag <thought>...</thought>.\n" +
                "- Se devi agire, inserisci la chiamata al tool SUBITO DOPO i pensieri.\n" +
                "- Se hai finito il lavoro o non servono tool, rispondi normalmente dopo il tag <thought>.";
        
        conversationHistory.clear();
        conversationHistory.add(systemPrompt);
    }

    private void runLoop(String userRequest) {
        // Rinfresca il contesto leggendo i file prima di ogni interazione
        initSystemPrompt();

        conversationHistory.add("\nRichiesta Utente: " + userRequest);
        
        int iteration = 0;
        boolean taskComplete = false;

        while (!taskComplete && iteration < MAX_ITERATIONS) {
            iteration++;
            System.out.println("\n🔄 [Loop Iterazione " + iteration + "] L'agente sta pensando...");

            String fullPrompt = String.join("\n", conversationHistory);
            String rawResponse = queryOllama(fullPrompt);

            String thought = extractTagContent(rawResponse, "thought");
            if (!thought.isEmpty()) {
                System.out.println("🧠 Pensiero Agente: " + thought);
            }

            String toolCall = extractToolCall(rawResponse);

            if (!toolCall.isEmpty()) {
                conversationHistory.add("\nRisposta Agente:\n" + rawResponse);
                System.out.println("🛠️ Esecuzione Tool rilevata: " + toolCall);
                
                String observation = executeTool(toolCall);
                System.out.println("👁️ Osservazione (Risultato): " + observation);

                conversationHistory.add("\nOBSERVATION FROM TOOL:\n" + observation);
            } else {
                String cleanResponse = rawResponse.replaceAll("<thought>.*?</thought>", "").trim();
                System.out.println("\n🤖 Risposta Finale Agente: " + cleanResponse);
                conversationHistory.add("\nRisposta Agente:\n" + rawResponse);
                taskComplete = true;
            }
        }

        if (iteration >= MAX_ITERATIONS) {
            System.out.println("⚠️ Loop interrotto: Raggiunto il limite massimo di iterazioni di sicurezza.");
        }
    }

    /**
     * Esecutore dei tool centralizzato dell'agente.
     */
    private String executeTool(String toolCall) {
        try {
            if (toolCall.startsWith("file_write")) {
                String filename = extractAttribute(toolCall, "filename");
                String content = toolCall.substring(toolCall.indexOf(">") + 1, toolCall.lastIndexOf("</call>"));
                
                Path path = Paths.get(filename);
                Files.writeString(path, content);
                return "{\"status\": \"success\", \"message\": \"File '" + filename + "' scritto correttamente.\"}";
                
            } else if (toolCall.startsWith("file_read")) {
                String filename = extractAttribute(toolCall, "filename");
                Path path = Paths.get(filename);
                
                if (Files.exists(path)) {
                    String content = Files.readString(path);
                    return "{\"status\": \"success\", \"content\": \"" + content.replace("\n", "\\n") + "\"}";
                } else {
                    return "{\"status\": \"error\", \"message\": \"File non trovato.\"}";
                }
                
            } else if (toolCall.startsWith("memory_append")) {
                // Estrae il blocco di testo contenuto dentro i tag <call:memory_append>...</call>
                String memoryData = toolCall.substring(toolCall.indexOf(">") + 1, toolCall.lastIndexOf("</call>")).trim();
                
                URL resourceUrl = HubberAgentLoop.class.getResource(MEMORY_RESOURCE);
                if (resourceUrl == null) {
                    return "{\"status\": \"error\", \"message\": \"File di memoria non trovato nel classpath.\"}";
                }
                Path path = Paths.get(resourceUrl.toURI());
                
                // Effettua l'append di una riga con un ritorno a capo finale
                String lineToAppend = "\n- " + memoryData;
                Files.writeString(path, lineToAppend, StandardOpenOption.APPEND);
                return "{\"status\": \"success\", \"message\": \"Nota aggiunta con successo a memory.md.\"}";
            }
        } catch (Exception e) {
            return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
        }
        return "{\"status\": \"error\", \"message\": \"Tool sconosciuto o sintassi errata.\"}";
    }

    private String queryOllama(String prompt) {
        String escapedPrompt = prompt.replace("\\", "\\\\")
                                     .replace("\"", "\\\"")
                                     .replace("\n", "\\n")
                                     .replace("\r", "\\r");

        String jsonPayload = "{\"model\":\"" + MODEL_NAME + "\",\"prompt\":\"" + escapedPrompt + "\",\"stream\":false}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            body = decodeUnicode(body);

            Pattern pattern = Pattern.compile("\"response\"\\s*:\\s*\"(.*?)\"\\s*\\s*(,\\s*\"[a-zA-Z0-9_-]+\"\\s*:)");
            Matcher matcher = pattern.matcher(body);
            
            if (matcher.find()) {
                String extracted = matcher.group(1);
                return extracted.replace("\\n", "\n")
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\");
            }
            return body; 
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "<thought>Errore di connessione a Ollama</thought>";
        }
    }

    private String decodeUnicode(String input) {
        Pattern pattern = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String extractTagContent(String text, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractToolCall(String text) {
        Pattern pattern = Pattern.compile("<call:(.*?)</call>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractAttribute(String text, String attribute) {
        Pattern pattern = Pattern.compile(attribute + "=\"(.*?)\"");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
}
