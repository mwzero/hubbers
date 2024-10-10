package com.ui.apps.mail.client;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroqTest {
	
	@Test
	public void test() throws Exception {
		
		log.info("Invoking model throught-out Groq");
    	
		EmbeddingModel model = OpenAiEmbeddingModel.builder()
				.baseUrl("https://api.groq.com/openai/v1/embeddings")
		        .apiKey("")
		        .modelName("llama3-8b-8192")
	            .timeout(Duration.ofMinutes(5))
	            .build();
		
		
		List<Float> embeddings = model.embed("la città di Praga è la capitale della Repubblica Ceca").content().vectorAsList();
		
		log.info("Embedding Size[{}]", embeddings.size());
    	
    	
        

    }
}
