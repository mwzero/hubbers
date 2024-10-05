package com.ui.apps;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.ui.apps.components.EmbeddingStoreGeneric;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrainingMailClassifier {
	
    public static void train(String rootFolder, 
    		EmbeddingStore<TextSegment> embeddingStore, 
    		EmbeddingModel embeddingModel) throws Exception {
    	
    	EmbeddingStoreGeneric store = new EmbeddingStoreGeneric(embeddingModel, embeddingStore);
    	File root = new File(rootFolder);
    	
    	for (File file : root.listFiles()) {
     	
			if (file.isDirectory()) {
				Gson gson = new Gson();
				JsonReader reader = new JsonReader(new FileReader(file.getAbsolutePath() + "/metadata.json"));
				Map<String, String> metadata = gson.fromJson(reader, Map.class);
				
				store.add(new File(file.getAbsolutePath() + "/content.txt"), metadata);
	    	}
    	}
    }
    
    public static void pretrain(String rootFolder, ChatLanguageModel chatModel) throws Exception {
		
    	String categorie = "Promozione, Social  News, Fatture da pagare, Abbonamenti in scadenza, Notizie dal medico, Altro";
    	PromptTemplate promptTemplate = PromptTemplate.from("""
    			Classifica il seguente testo di email in una delle seguenti categorie:
    			{{categorie}}
    			Email:
    			{{email}}

    			Rispondi con il nome della categoria più appropriata tra quelle indicate sopra.
    			""");
    	
    	File root = new File(rootFolder);
    	
    	for (File file : root.listFiles()) {
     	
			if (file.isDirectory()) {
				 
				Gson gson = new Gson();
				JsonReader reader = new JsonReader(new FileReader(
						file.getAbsolutePath() + "/metadata.json"));
				Map<String, String> metadata = gson.fromJson(reader, Map.class);
				
				String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath() + "/content.txt")));
				
				Map<String, Object> variables = new HashMap<>();
	            variables.put("categorie", categorie);
	            variables.put("email", content);

	            Prompt prompt = promptTemplate.apply(variables);
	            AiMessage message = chatModel.generate(prompt.toUserMessage()).content();
	            String categories = message.text();
				
				
				metadata.put("categories", String.join(",", categories));
				try (Writer writer = new FileWriter(file.getAbsolutePath() + "/metadata.json")) {
				    gson.toJson(metadata, writer);
				}
	    	}
    	}
	}
    
    public static void main(String[] args) throws Exception {
    	
        String dbPgHost = System.getenv("DB_PG_HOST");
		int dbPgPort = Integer.parseInt(System.getenv("DB_PG_PORT"));
		String dbPgUser = System.getenv("DB_PG_USER");
		String dbPgPwd = System.getenv("DB_PG_PWD");
    		
    		
	    String rootFolder = "C:\\temp\\mail-assistant-training\\";
            
	    log.info("Store: [{}] [{}] [{}]", dbPgHost, dbPgPort, dbPgUser);
    		
		EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
	            .baseUrl("http://localhost:11434")
	            .modelName("llama2:7b")
	            .timeout(Duration.ofMinutes(5))
	            .build();
		
		ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .timeout(Duration.ofMinutes(10))
                //.modelName("llama3")
                .modelName("llama3:instruct")
                //.modelName("llama2:7b")
                .build();
		
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
		
		//TrainingMailClassifier.train(rootFolder, embeddingStore, embeddingModel);
		TrainingMailClassifier.pretrain(rootFolder, chatModel);
    	
    }
    
}
