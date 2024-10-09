package com.ui.apps.components;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.time.Duration;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.ui.apps.utils.EmbeddingStoreGeneric;
import com.ui.apps.utils.FileNameHashGenerator;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailIngestor {
	
	EmbeddingStoreGeneric store;
	EmbeddingStoreIngestor embeddingStoreIngestor;
	
	public MailIngestor(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) throws Exception {
		
    	store = new EmbeddingStoreGeneric(embeddingModel, embeddingStore);
    	embeddingStoreIngestor = EmbeddingStoreIngestor.builder()
             	.documentSplitter(DocumentSplitters.recursive(300, 0))
             	.embeddingModel(embeddingModel)
             	.embeddingStore(embeddingStore)
             	.build();
	}
    	
    
	public void process(String rootFolder, boolean onlyContent) throws Exception {
		
    	File root = new File(rootFolder);
    	
    	for (File file : root.listFiles()) {
     	
			if (file.isDirectory()) {
				 
				Gson gson = new Gson();
				JsonReader reader = new JsonReader(new FileReader(file.getAbsolutePath() + "/metadata.json"));
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
					if ( onlyContent ) {
						if ( file2Process.getAbsolutePath().contains("content.txt")) {
							store.add(file2Process, metadata);
						}
						
					} else {
							
						Document document;
						if ( "pdf".compareTo(FileNameHashGenerator.getFileExtension(file2Process)) == 0 )
							document = loadDocument(file2Process.toPath(), new ApachePdfBoxDocumentParser());
						else 
							document = loadDocument(file2Process.toPath(), new TextDocumentParser());
						
						embeddingStoreIngestor.ingest(document);
					}
				}
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
		
		MailIngestor ingestor = new MailIngestor(embeddingStore, embeddingModel);
		ingestor.process(rootFolder, true);
		
    }
}
