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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
	
	public void processFolder(String rootFolder, ChatLanguageModel chatModel) throws Exception {
		
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
	            log.info("categori[{}]", categorie);
				metadata.put("categories", String.join(",", categories));
				MailFileHelper.setMetaDataFile(file.getAbsolutePath(), metadata);
	    	}
    	}
	}
    	
    public void processFolder(String rootFolder, ChatLanguageModel chatModel, PromptTemplate promptTemplate) throws Exception {
		
    	File root = new File(rootFolder);
    	
    	for (File file : root.listFiles()) {
    		
    		log.info("Classifing folder [{}]", file.getCanonicalPath());
     	
			if (file.isDirectory()) {
				 
				String content = MailFileHelper.getContent(file.getAbsolutePath());
				String categorie = "Promozione, Social  News, Fatture da pagare, Abbonamenti in scadenza, Notizie dal medico, Altro";
		    	
				Map<String, Object> variables = new HashMap<>();
	            variables.put("categorie", categorie);
	            variables.put("email", content);

	            Prompt prompt = promptTemplate.apply(variables);
	            AiMessage message = chatModel.generate(prompt.toUserMessage()).content();
	            String categories = message.text();
	            log.info("Category AI[{}]", categories);
	            
	            String jsonOutput = categories.replaceAll("(?s).*```(.*)```.*", "$1").trim();
	            JsonObject jsonObject = JsonParser.parseString(jsonOutput).getAsJsonObject();
	            String categoria = jsonObject.get("category").getAsString();
	            log.info("categori[{}]", categoria);
	            Map<String, String> metadata = MailFileHelper.getMetaDataFile(file.getAbsolutePath());
	            metadata.put("categories", categoria);
				//metadata.put("categories", String.join(",", categories));
				MailFileHelper.setMetaDataFile(file.getAbsolutePath(), metadata);
	    	}
    	}
    	
    	
	}
    
    public void process(String content, ChatLanguageModel chatModel, PromptTemplate promptTemplate) throws Exception {
		
			String categorie = "Promozione, Social  News, Fatture da pagare, Abbonamenti in scadenza, Notizie dal medico, Altro";
	    	
			Map<String, Object> variables = new HashMap<>();
            variables.put("categorie", categorie);
            variables.put("email", content);

            Prompt prompt = promptTemplate.apply(variables);
            AiMessage message = chatModel.generate(prompt.toUserMessage()).content();
            String categories = message.text();
            log.info("Category AI[{}]", categories);
            
            String jsonOutput = categories.replaceAll("(?s).*```(.*)```.*", "$1").trim();
            JsonObject jsonObject = JsonParser.parseString(jsonOutput).getAsJsonObject();
            String categoria = jsonObject.get("category").getAsString();
            log.info("categorie[{}]", categoria);
	}
    
    public static void main(String[] args) throws Exception {
    	
    	log.debug("Acquiring parameters via System-env");
    	String dbPgHost = System.getenv("DB_PG_HOST");
		int dbPgPort = Integer.parseInt(System.getenv("DB_PG_PORT"));
		String dbPgUser = System.getenv("DB_PG_USER");
		String dbPgPwd = System.getenv("DB_PG_PWD");
        String rootFolder = System.getenv("ROOT_FOLDER");
        log.info("WorkingFolder [{}] Store: [{}] [{}] [{}]",
				rootFolder,
				dbPgHost, 
				dbPgPort, 
				dbPgUser);
        
        String username = System.getenv("GMAIL_USERNAME");
        String password = System.getenv("GMAIL_PASSWORD");
        log.info("Reading mail for [{}] writing to [{}]", username, rootFolder);
    	
		ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .timeout(Duration.ofMinutes(10))
                //.modelName("llama3")
                //.modelName("llama3:instruct")
                //.modelName("gemma2")
                //.modelName("mistral-nemo")
                .modelName("llama3.2:1b")
                .format("json")
                //.modelName("llama2:7b")
                .build();
		
		TrainingMailClassifier training = new TrainingMailClassifier();		
		
		PromptTemplate promptTemplate = PromptTemplate.from("""
    			Classifica il seguente testo in una delle seguenti categorie: {{categorie}}. 
    			Restituisci solo l'output in formato JSON con il campo "category". 
    			Testo: "{{email}}"
    			""");
		
		training.processFolder(rootFolder, chatModel, promptTemplate);
		
		
		/*
		String content = """
Ciao Maurizio,

Ciro Grossi ha condiviso un link

Grazie,
Il team di Facebook



========================================
Questo messaggio è stato inviato a maurizio.farina@gmail.com. Se non vuoi più ricevere questo tipo di e-mail da Meta, segui il link per disattivarne la ricezione.
https://www.facebook.com/o.php?k=AS0Xlb058a0wb_-sHfk&u=1422135690&mid=6241d1db74c40G54c4118aG6240a69c5e56dG318&ee=AY39KlyAozgp8q796KtxbedzPzzBhRTff28axSX_F1d92eSL0c4PY8SoauZMu1Qwcc9DjsZTNCN9O_2GgP3emRt18Q
Meta Platforms Ireland Ltd., Attention: Community Operations, 4 Grand Canal Square, Dublin 2, Ireland
Per contribuire a proteggere il tuo account, non inoltrare questa e-mail. Segui il link in basso per scoprire di più.
https://www.facebook.com/email_forward_notice/?mid=6241d1db74c40G54c4118aG6240a69c5e56dG318
	""";
		
		training.process(content, chatModel, promptTemplate);
		*/
		
    	
    }
    
}
