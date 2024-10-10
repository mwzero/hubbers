package com.ui.apps.components;

import java.io.File;
import java.time.Duration;
import java.util.Map;

import com.ui.apps.mail.utils.MailFileHelper;
import com.ui.apps.utils.EmbeddingStoreGeneric;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailClassifier {
	
	EmbeddingStoreGeneric store;
	
	public MailClassifier(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) throws Exception {
		
		store = new EmbeddingStoreGeneric(embeddingModel,embeddingStore );
		
	}
    
	public void process(String rootFolder) throws Exception {
		
    	File root = new File(rootFolder);
    	
    	for (File file : root.listFiles()) {
     	
			if (file.isDirectory()) {
				Map<String, String> metadata = MailFileHelper.getMetaDataFile(file.getAbsolutePath());
				
				EmbeddingMatch<TextSegment> category = store.query(MailFileHelper.getContent(file.getAbsolutePath()));
				category.score(); // 0.8144288515898701
				category.embedded().text(); // I like football.
				
				String categories = category.embedded().metadata("categories");
				
				metadata.put("categories", String.join(",", categories));
				MailFileHelper.setMetaDataFile(file.getAbsolutePath() , metadata);
	    	}
    	}
	}
	
	public static void main(String[] args) throws Exception {
    	
		String dbPgHost = System.getenv("DB_PG_HOST");
		int dbPgPort = Integer.parseInt(System.getenv("DB_PG_PORT"));
		String dbPgUser = System.getenv("DB_PG_USER");
		String dbPgPwd = System.getenv("DB_PG_PWD");
        String rootFolder = "C:\\temp\\mail-assistant";
        log.info("Store: [{}] [{}] [{}]", dbPgHost, dbPgPort, dbPgUser);
        
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
		
		
		
		MailClassifier classifier = new MailClassifier(embeddingStore, embeddingModel);
		classifier.process(rootFolder);
    	
    }
}
