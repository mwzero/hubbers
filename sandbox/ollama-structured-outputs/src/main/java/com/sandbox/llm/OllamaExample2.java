package com.sandbox.llm;

import java.time.Duration;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class OllamaExample2 {

    public static void main(String[] args) {
    	
    	/*
    	ChatLanguageModel model =  OpenAiChatModel.builder()
    			.baseUrl("https://api.groq.com/openai/v1")
    	        .apiKey(System.getenv("GROQ_API_KEY"))
		        .modelName("llama3-8b-8192")
		        .timeout(Duration.ofMinutes(5))
    	        .build();
    	*/
    	ResponseFormat responseFormat = ResponseFormat.builder()
    	        .type(ResponseFormatType.JSON) // type can be either TEXT (default) or JSON
    	        .jsonSchema(JsonSchema.builder()
    	                .name("Person") // OpenAI requires specifying the name for the schema
    	                .rootElement(JsonObjectSchema.builder() // see [1] below
    	                        .addStringProperty("name")
    	                        .addIntegerProperty("age")
    	                        .addNumberProperty("height")
    	                        .addBooleanProperty("married")
    	                        .required("name", "age", "height", "married") // see [2] below
    	                        .build())
    	                .build())
    	        .build();
    	
    	UserMessage userMessage = UserMessage.from("""
    	        John is 42 years old and lives an independent life.
    	        He stands 1.75 meters tall and carries himself with confidence.
    	        Currently unmarried, he enjoys the freedom to focus on his personal goals and interests.
    	        """);

    	ChatRequest chatRequest = ChatRequest.builder()
    	        .responseFormat(responseFormat)
    	        .messages(userMessage)
    	        .build();
    	
    	ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2:1b")
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofMinutes(5))
                .build();


        ChatResponse chatResponse  = model.chat(chatRequest);

        // Print the raw JSON to inspect
        System.out.println("=== Raw JSON from Ollama ===");
        System.out.println(chatResponse.aiMessage().text());

    }
}