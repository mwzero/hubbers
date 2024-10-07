package com.ui.apps;

import static com.jui.JuiApp.jui;
import static com.st.ST.st;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.st.DataFrame;
import com.ui.apps.components.EmbeddingStoreGeneric;

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
        
        //jui.set_page_config().rootDoc("sidebar");
    	
    	jui.markdown("""
    			# Dashboard: Mail Assistant
    			""");
    	jui.divider();
    	
    	jui.markdown("""
    			## PostgreSQL Database settings
    			""");
    	jui.divider();
    	
    	jui.input.input("PostregSQL Host",  dbPgHost, "PostgreSQL host address");
    	jui.input.input("PostregSQL Port",  dbPgPort + "", "PostgreSQL host port");
    	jui.input.input("PostregSQL User",  dbPgUser, "PostgreSQL host address");
    	jui.input.input("PostregSQL Password",  dbPgPwd, "PostgreSQL host address");
    	
    	
    	st.setOptions(Map.of("classLoading", "true"));
    	
    	try {
    		DataFrame df = st
    					 .read_json("../../../../datasets/dpc-covid19-ita-province.zip")
    					.select(
    						List.of("data",
    						"denominazione_regione",
    						"denominazione_provincia",
    						"sigla_provincia",
    						"lat", "long",
    						"totale_casi"));

    		jui.table("Covid", df, 4);
    	
    	} catch ( Exception err) {
    		
    		log.error(err.getLocalizedMessage());
    	}
    	
    	jui.start();
		
		ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .timeout(Duration.ofMinutes(10))
                //.modelName("llama3")
                .modelName("llama3")
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

		//EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
		EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
	            .baseUrl("http://localhost:11434")
	            .modelName("llama2:7b")
	            .timeout(Duration.ofMinutes(5))
	            .build();

		EmbeddingStoreGeneric store = new EmbeddingStoreGeneric(embeddingModel,embeddingStore );
		
    }
}
