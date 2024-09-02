package com.hubbers.core.agents;

import java.util.Map;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import lombok.Builder;
import lombok.Setter;

@Builder
public class SentimentAnalyzer {

	static ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("orca-mini")
            .build();
	
    public enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE;
    }

    public interface SentimentAnalyzerProcessor {

        @UserMessage("Analyze sentiment of {{it}}")
        Sentiment analyzeSentimentOf(String text);

        @UserMessage("Does {{it}} have a positive sentiment?")
        boolean isPositive(String text);
    }

    public Sentiment getSentiment(String text) {

    	SentimentAnalyzerProcessor sentimentAnalyzer = AiServices.create(SentimentAnalyzerProcessor.class, chatLanguageModel);

        return sentimentAnalyzer.analyzeSentimentOf(text);
    }
}
