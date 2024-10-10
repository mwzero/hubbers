package com.ui.apps;

import static com.jui.JuiApp.jui;
import static com.st.ST.st;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.ui.apps.components.MailClassifier;
import com.ui.apps.components.MailIngestor;
import com.ui.apps.components.MailReader;
import com.ui.apps.components.TrainingMailClassifier;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JuiMailAssistant {
	
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
        
        
        /* initialize AI components */
        ChatLanguageModel chatModel = initializeChatModel();
        EmbeddingStore<TextSegment> embeddingStore = initializeEmbeddingStore();
        EmbeddingModel embeddingModel = initializeEmbeddingModel();
        /*
        ChatLanguageModel chatModel = null;
        EmbeddingStore<TextSegment> embeddingStore = null;
        EmbeddingModel embeddingModel = null;
        */
        
        /*
        jui.set_page_config().rootDoc("sidebar-toolbar");
 		jui.sidebar.markdown("""
 				# Mail Assistant App
 				
 				**Overview:** A collection of AI agents around e-mail
 				**About the Developer:** I'm Maurizio Farina. Let's connect on LinkedIn: https://www.linkedin.com/in/farinamaurizio/
 				**Source Code:** You can access the code on GitHub
 				""");
 		
 		jui.sidebar.dropDownButton("Settings", List.of("Profile", "Account Settings", "Logout"));
 		*/    	
     		
    	jui.markdown("""
    			# Mail Assistant
    			""");
    	jui.divider();
    	
    	/*
    	jui.markdown("""
    			## PostgreSQL Database settings
    			""");
    	jui.divider();
    	
    	jui.input.input("PostregSQL Host",  dbPgHost, "PostgreSQL host address");
    	jui.input.input("PostregSQL Port",  dbPgPort + "", "PostgreSQL host port");
    	jui.input.input("PostregSQL User",  dbPgUser, "PostgreSQL host address");
    	jui.input.input("PostregSQL Password",  dbPgPwd, "PostgreSQL host address");
    	*/
    	
    	st.setOptions(Map.of("classLoading", "true"));
    	
    	jui.button("Donwload Mail","primary", "", () -> {
    		log.info("Reading mail for [{}] writing to [{}]", username, rootFolder);
    		MailReader mailReader = new MailReader();
            try {
				mailReader.process(rootFolder, username, password);
        		jui.table("Mails", mailReader.getDF().select(
						List.of("Sender","Date","Subject")).limit(100));
				
				
			} catch (Exception e) {
				
				log.error("Error [{}]", e.getLocalizedMessage());
				jui.setJuiResponse(e.getLocalizedMessage());
				
			}
        
    	});
    	
    	jui.button("Training Classifier","primary", "", () -> {
    		
            try {
            	
            	TrainingMailClassifier training = new TrainingMailClassifier();		
        		training.process(rootFolder, chatModel);
        		
			} catch (Exception e) {
				
				log.error("Error [{}]", e.getLocalizedMessage());
				jui.setJuiResponse(e.getLocalizedMessage());
			}
        
    	});
    	
    	jui.button("Classify Mail","primary", "", () -> {
    		
            try {
            	MailClassifier classifier = new MailClassifier(embeddingStore, embeddingModel);
            	classifier.process(rootFolder);
				
			} catch (Exception e) {
				
				log.error("Error [{}]", e.getLocalizedMessage());
				jui.setJuiResponse(e.getLocalizedMessage());
			}
        
    	});
    	
    	jui.button("Ingest Mail","primary", "", () -> {
    		
    		
            try {
            	MailIngestor ingestor = new MailIngestor(embeddingStore, embeddingModel);
            	ingestor.process(rootFolder, false);
				
			} catch (Exception e) {
				
				log.error("Error [{}]", e.getLocalizedMessage());
				jui.setJuiResponse(e.getLocalizedMessage());
			}
        
    	});
    	
    	jui.start();
		
    }
    
	public static ChatLanguageModel initializeChatModel() {
		 //building models
	    return OllamaChatModel.builder()
	            .baseUrl("http://localhost:11434")
	            .timeout(Duration.ofMinutes(10))
	            //.modelName("llama3")
	            .modelName("llama3")
	            .build();
	}
	
	public static EmbeddingStore<TextSegment> initializeEmbeddingStore() {
		return PgVectorEmbeddingStore.builder()
	        .host(System.getenv("DB_PG_HOST"))
	        .port(Integer.parseInt(System.getenv("DB_PG_PORT")))
	        .user(System.getenv("DB_PG_USER"))
	        .password(System.getenv("DB_PG_PWD"))
	        .database("postgres")
	        .table("mailclassifier")
	        .dimension(4096)
	        //.dropTableFirst(true)
	        .build();
	}
	
	public static EmbeddingModel initializeEmbeddingModel() {
		//EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
		return OllamaEmbeddingModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("llama2:7b")
            .timeout(Duration.ofMinutes(5))
            .build();
	}
}
