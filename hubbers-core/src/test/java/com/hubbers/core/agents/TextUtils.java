package com.hubbers.core.agents;

import java.util.List;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class TextUtils {
	
	static ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("orca-mini")
            .format("json")
            .build();
	
	interface TextUtilsProcessor {

        @SystemMessage("You are a professional translator into {{language}}")
        @UserMessage("Translate the following text: {{text}}")
        String translate(@V("text") String text, @V("language") String language);

        @SystemMessage("Summarize every message from user in {{n}} bullet points. Provide only bullet points.")
        List<String> summarize(@UserMessage String text, @V("n") int n);
    }
	
	interface TextUtilsProcessorFromFile {

        @SystemMessage(fromResource = "/translator-system-prompt-template.txt")
        @UserMessage(fromResource = "/translator-user-prompt-template.txt")
        String translate(@V("text") String text, @V("language") String language);
    }

    public List<String> process(String text) {

    	TextUtilsProcessor utils = AiServices.create(TextUtilsProcessor.class, chatLanguageModel);
        //String translation = utils.translate("Hello, how are you?", "italian");

        List<String> bulletPoints = utils.summarize(text, 3);
        System.out.println(bulletPoints);
		return bulletPoints;
    }

}
