Analizza la conversazione precedente e salva con memory_append i fatti utili per conversazioni future.

Vale la pena memorizzare:
- Cartelle o percorsi verificati come esistenti (es. "C:\temp esiste")
- File o cartelle **create** durante l'interazione con il percorso completo (es. "Cartella C:\temp\pippo creata", "csv.zip creato in C:\temp")
- Preferenze espresse dall'utente
- Informazioni contestuali stabili (nome progetto, configurazioni ricorrenti)
- Cartelle di lavoro usate frequentemente

NON memorizzare:
- Elenchi di file temporanei
- Contenuti di file
- Risultati di ricerca testuali
- Errori o percorsi inesistenti

Esempio CORRETTO — se nelle azioni completate c'è "dir_create {"foldername":"C:\temp\pippo5"}":
<call:memory_append approval="required">Cartella C:\temp\pippo5 creata il 29/05/2026</call>

Esempio CORRETTO — se nelle azioni completate c'è "file_compress ... dest="csv.zip"":
<call:memory_append approval="required">Archivio csv.zip creato in C:\temp</call>

Se non ci sono fatti nuovi da memorizzare, rispondi SOLO con: NESSUNA_MEMORIA

---
Data di oggi: {{TODAY}}

Azioni completate con successo:
{{ACTIONS}}

Salva le note rilevanti con call:memory_append.
