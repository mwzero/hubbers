package com.sandbox.llm;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class OllamaExample {

    public static void main(String[] args) {
    	
    	ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2:1b")
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

            Populate the 'countries' array with 10 entries for the top 10 nations by population.
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

        // Parse the JSON into our POJOs
        System.out.println("\n=== Parsed Response ===");
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Country> response = mapper.readValue(rawJsonOutput, new TypeReference<List<Country>>() {});

            response.stream().forEach( country -> {
            	
                System.out.println("Name: " + country.getName());
                System.out.println("Population: " + country.getPopulation());
                System.out.println("Graduates %: " + country.getGraduatesDistributionPct());
                System.out.println("GDP for R&D/Education %: " + country.getGdpResearchEducationPct());
                System.out.println("---------------------------------------");
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}