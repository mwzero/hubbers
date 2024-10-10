package com.ui.apps.components;

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
import com.ui.apps.mail.utils.MailFileHelper;
import com.ui.apps.utils.EmbeddingStoreGeneric;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrainingMailClassifier {
	
	public TrainingMailClassifier() throws Exception {
	}
	
    public void process(String rootFolder, ChatLanguageModel chatModel) throws Exception {
		
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
				 
				Map<String, String> metadata = MailFileHelper.getMetaDataFile(file.getAbsolutePath());
				
				String content = MailFileHelper.getContent(file.getAbsolutePath());
				String categorie = "Promozione, Social  News, Fatture da pagare, Abbonamenti in scadenza, Notizie dal medico, Altro";
		    	
				Map<String, Object> variables = new HashMap<>();
	            variables.put("categorie", categorie);
	            variables.put("email", content);

	            Prompt prompt = promptTemplate.apply(variables);
	            AiMessage message = chatModel.generate(prompt.toUserMessage()).content();
	            String categories = message.text();
				
				
				metadata.put("categories", String.join(",", categories));
				MailFileHelper.setMetaDataFile(file.getAbsolutePath(), metadata);
	    	}
    	}
	}
    
    public static void main(String[] args) throws Exception {
    	
    	String rootFolder = "C:\\temp\\mail-assistant";
    	
		ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .timeout(Duration.ofMinutes(10))
                //.modelName("llama3")
                .modelName("llama3:instruct")
                //.modelName("llama2:7b")
                .build();
		
		TrainingMailClassifier training = new TrainingMailClassifier();		
		training.process(rootFolder, chatModel);
    	
    }
    
}
