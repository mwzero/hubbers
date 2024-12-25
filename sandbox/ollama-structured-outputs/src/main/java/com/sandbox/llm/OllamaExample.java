package com.sandbox.llm;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class OllamaExample {

    public static void main(String[] args) {
    	
    	/*
    	ChatLanguageModel model =  OpenAiChatModel.builder()
    			.baseUrl("https://api.groq.com/openai/v1")
    	        .apiKey(System.getenv("GROQ_API_KEY"))
		        .modelName("llama3-8b-8192")
		        .timeout(Duration.ofMinutes(5))
    	        .build();
    	*/
    	
    	ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2:1b")
                .responseFormat(ResponseFormat.JSON)
                .timeout(Duration.ofMinutes(5))
                .build();


        // Define a prompt that asks for strict JSON output
        String prompt = """
            You are a helpful assistant. Please output the following data as JSON:
            {
              "countries": [
                {
                  "name": "...",
                  "population": 0,
                  "graduatesDistributionPct": 0,
                  "gdpResearchEducationPct": 0
                }
              ]
            }

            Populate the 'countries' array with 100 entries for the top 10 nations by population.
            Use realistic data for:
            - name
            - population
            - graduatesDistributionPct
            - gdpResearchEducationPct

            Only return valid JSON without additional commentary.
            """;

        // Call Ollama via our custom LLM
        String rawJsonOutput = model.generate(prompt);

        // Print the raw JSON to inspect
        System.out.println("=== Raw JSON from Ollama ===");
        System.out.println(rawJsonOutput);

    }
}