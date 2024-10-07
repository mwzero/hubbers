package com.ui.apps;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.time.Duration;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.ui.apps.components.EmbeddingStoreGeneric;
import com.ui.apps.utils.FileNameHashGenerator;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailSummarize {
	
    
	public static String summarize(String rootFolder, ChatLanguageModel chatModel) throws Exception {
		
	   	File root = new File(rootFolder);
	   	
	   	StringBuilder subjects = new StringBuilder(); 
	   	for (File file : root.listFiles()) {
    	
			if (file.isDirectory()) {
				
				Gson gson = new Gson();
				JsonReader reader = new JsonReader(new FileReader(file.getAbsolutePath() + "/metadata.json"));
				Map<String, String> metadata = gson.fromJson(reader, Map.class);
				
				subjects.append(metadata.get("subject"));
				
			}
	   	
	   	}
	   	
	   	PromptTemplate promptTemplate = PromptTemplate.from("""
    			Di seguito sono riportate alcuni titoli di email: 
    			{{email}}
    			
    			Riassumi queste email cercando di evidenziare quelle che richiedono un'attività da parte di chi le riceve.
    			Scrivi un sommario in italiano riportanto titolo della mail ed azione da intraprendere:  
    			""");
	   	
	   	AiMessage message = chatModel.generate(promptTemplate.apply(Map.of("email",subjects.toString())).toUserMessage()).content();
	   	return message.text();
	   	
	}

    public static void main(String[] args) throws Exception {
    	
    	
        String rootFolder = "C:\\temp\\mail-assistant";
        
		ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .timeout(Duration.ofMinutes(10))
                //.modelName("llama3")
                .modelName("llama3")
                .build();

		String summary = summarize(rootFolder, chatModel);
		log.info("summary[{}]", summary);
    	
    	
        

    }
}
