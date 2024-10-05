package com.ui.apps;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.time.Duration;
import java.util.Map;

import javax.mail.Flags;
import javax.mail.search.FlagTerm;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.ui.apps.components.MailEmbeddingStore;
import com.ui.apps.components.MailExtractorSinker;
import com.ui.apps.mail.GoogleEmailReader;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailAssistant {
	
	public static void reader(String rootFolder, String username, String password) {
		
		MailExtractorSinker sinker = 
				MailExtractorSinker.builder().rootDirectory(rootFolder).build();
		
		
		GoogleEmailReader
			.builder()
			.username(username)
			.password(password)
			.host("imap.gmail.com")
			.port("993")
			.folder("INBOX")
			.flagTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
		.build()
		.process(sinker);
		
	}
	
	public static void embedding(String rootFolder, EmbeddingModel embeddingModel) throws Exception {
		
    	MailEmbeddingStore store = new MailEmbeddingStore(
    			embeddingModel, 
    			"mailAssistent", 
    			"mail");
    	
    	File root = new File(rootFolder);
    	
    	for (File file : root.listFiles()) {
     	
			if (file.isDirectory()) {
				 
				Gson gson = new Gson();
				JsonReader reader = new JsonReader(new FileReader(
						file.getAbsolutePath() + "/metadata.json"));
				Map<String, String> metadata = gson.fromJson(reader, Map.class);
				for (File file2Process : file.listFiles(
							new FileFilter() {
				        		@Override
				        		public boolean accept(File pathname) {
				        			
				        			if ( pathname.getAbsolutePath().contains("metadata.json"))
				        				return false;
				        			
				        			return true;
				        			
				        		}
							})) {
						
					log.debug("Adding File[{}]", file2Process.getAbsolutePath());
					store.add(file2Process, metadata);
				}
	    	}
    	}
	}

    public static void main(String[] args) throws Exception {
    	
    	String username = System.getenv("GMAIL_USERNAME");
        String password = System.getenv("GMAIL_PASSWORD");
        String dbPgHost = System.getenv("DB_PG_HOST");
		int dbPgPort = Integer.parseInt(System.getenv("DB_PG_PORT"));
		String dbPgUser = System.getenv("DB_PG_USER");
		String dbPgPwd = System.getenv("DB_PG_PWD");
		
		
        String rootFolder = "C:\\temp\\mail-assistant";
        
        
        log.info("Reading mail for [{}]", username);
		reader ( rootFolder, username, password);
		
        
        /*
    	ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .timeout(Duration.ofMinutes(10))
                //.modelName("llama3")
                .modelName("llama3:instruct")
                .build();
        */
		/*
		EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
		embedding(rootFolder, embeddingModel);
    	log.info("Store: [{}] [{}] [{}]", dbPgHost, dbPgPort, dbPgUser);
    	*/
        

    }
}
