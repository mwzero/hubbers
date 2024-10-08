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
    	
		EmbeddingModel model = OpenAiEmbeddingModel.builder()
				.baseUrl("https://api.groq.com/openai/v1/embeddings")
		        .apiKey("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")
		        .modelName("llama3-8b-8192")
	            .timeout(Duration.ofMinutes(5))
	            .build();
		
		
		List<Float> embedding = model.embed("la città di Praga è la capitale della Repubblica Ceca").content().vectorAsList();
		
		System.out.println(embedding.size());
    	
    	
        

    }
}
