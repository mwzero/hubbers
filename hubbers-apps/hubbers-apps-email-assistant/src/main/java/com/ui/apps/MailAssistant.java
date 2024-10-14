package com.ui.apps;

import static com.jui.JuiApp.jui;
import static com.st.ST.st;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.ui.apps.components.MailClassifier;
import com.ui.apps.components.MailReader;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailAssistant {
	
    public static void main(String[] args) throws Exception {
    	
    	String dbPgHost = System.getenv("DB_PG_HOST");
		int dbPgPort = Integer.parseInt(System.getenv("DB_PG_PORT"));
		String dbPgUser = System.getenv("DB_PG_USER");
		String dbPgPwd = System.getenv("DB_PG_PWD");
        String rootFolder = "C:\\temp\\mail-assistant";
        log.info("WorkingFolder [{}] Store: [{}] [{}] [{}]",
				rootFolder,
				dbPgHost, 
				dbPgPort, 
				dbPgUser);
        
        String username = System.getenv("GMAIL_USERNAME");
        String password = System.getenv("GMAIL_PASSWORD");
        log.info("Reading mail for [{}] writing to [{}]", username, rootFolder);
        
		EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
            .host(System.getenv("DB_PG_HOST"))
            .port(Integer.parseInt(System.getenv("DB_PG_PORT")))
            .user(System.getenv("DB_PG_USER"))
            .password(System.getenv("DB_PG_PWD"))
            .database("postgres")
            .table("mailclassifier")
            .dimension(4096)
            //.dropTableFirst(true)
            .build();

		//EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
		EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
	            .baseUrl("http://localhost:11434")
	            .modelName("llama3")
	            .timeout(Duration.ofMinutes(5))
	            .build();
		
		

    }
}
