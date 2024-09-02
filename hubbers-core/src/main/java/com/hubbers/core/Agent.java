package com.hubbers.core;

import java.util.Map;

import org.springframework.context.annotation.Bean;

import com.hubbers.core.model.AgentResponse;
import com.hubbers.core.tools.java.JavaCodeExtractor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class Agent {
	
	String role;
	String goal;
	boolean memory;
	boolean verbose;
	String backstory;
	
	static String MODEL_NAME = "orca-mini"; // try "mistral", "llama2", "codellama", "phi" or "tinyllama"
	
	static ChatLanguageModel model = OllamaChatModel.builder()
	                .baseUrl("http://localhost:11434")
	                .modelName(MODEL_NAME)
	                .build();
	
	public AgentResponse execute(Map<String, String> inputs, String target) {
		
		PromptTemplate userTemplate = new PromptTemplate(target);
		PromptTemplate systemTemplate = new PromptTemplate(backstory);
		
		Response<AiMessage> output = model.generate(
				systemTemplate.apply(inputs).toSystemMessage(), 
				userTemplate.apply(inputs).toUserMessage());
		
		return JavaCodeExtractor.extract(output.content().text());
		
	}
	
	public String execute(String inputs) {
		
		PromptTemplate userTemplate = new PromptTemplate(goal);
		PromptTemplate systemTemplate = new PromptTemplate(backstory);
		
		Response<AiMessage> output = model.generate(
				systemTemplate.apply(inputs).toUserMessage(), 
				userTemplate.apply(inputs).toUserMessage());
		return output.content().text();
		
	}

	/*
	static String baseUrl() {
        return String.format("http://%s:%d", ollama.getHost(), ollama.getFirstMappedPort());
    }
    */
	
	/*
	void json_output_example() {

        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl())
                .modelName(MODEL_NAME)
                .format("json")
                .build();

        String json = model.generate("Give me a JSON with 2 fields: name and age of a John Doe, 42");

        System.out.println(json);
    }
    */

}