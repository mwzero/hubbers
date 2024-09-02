package com.hubbers.core.agents;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import lombok.Builder;

//https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/OtherServiceExamples.java
@Builder
public class DataExtractor {
	
	static ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("orca-mini")
            //.format("json")
            .build();
	
	interface NumberExtractor {

        @UserMessage("Extract number from {{it}}")
        int extractInt(String text);

        @UserMessage("Extract number from {{it}}")
        long extractLong(String text);

        @UserMessage("Extract number from {{it}}")
        BigInteger extractBigInteger(String text);

        @UserMessage("Extract number from {{it}}")
        float extractFloat(String text);

        @UserMessage("Extract number from {{it}}")
        double extractDouble(String text);

        @UserMessage("Extract number from {{it}}")
        BigDecimal extractBigDecimal(String text);
    }
	
	interface DateTimeExtractor {

        @UserMessage("Extract date from {{it}}")
        LocalDate extractDateFrom(String text);

        @UserMessage("Extract time from {{it}}")
        LocalTime extractTimeFrom(String text);

        @UserMessage("Extract date and time from {{it}}")
        LocalDateTime extractDateTimeFrom(String text);
    }
	
	
	class Person {

        private String firstName;
        private String lastName;
        private LocalDate birthDate;

        @Override
        public String toString() {
            return "Person {" +
                    " firstName = \"" + firstName + "\"" +
                    ", lastName = \"" + lastName + "\"" +
                    ", birthDate = " + birthDate +
                    " }";
        }
    }
	
	interface PersonExtractorProcessor {

        @UserMessage("Extract information about a person from {{it}}")
        Person extractPersonFrom(String text);
    }
	
	public Person getPerson(String text) {

		PersonExtractorProcessor extractor = AiServices.create(PersonExtractorProcessor.class, chatLanguageModel);
        return extractor.extractPersonFrom(text);
    }
	
	public void ab(String text) {
		
		NumberExtractor extractor = AiServices.create(NumberExtractor.class, chatLanguageModel);
		int intNumber = extractor.extractInt(text);
        System.out.println(intNumber); // 42

        long longNumber = extractor.extractLong(text);
        System.out.println(longNumber); // 42

        BigInteger bigIntegerNumber = extractor.extractBigInteger(text);
        System.out.println(bigIntegerNumber); // 42

        float floatNumber = extractor.extractFloat(text);
        System.out.println(floatNumber); // 42.0

        double doubleNumber = extractor.extractDouble(text);
        System.out.println(doubleNumber); // 42.0

        BigDecimal bigDecimalNumber = extractor.extractBigDecimal(text);
        System.out.println(bigDecimalNumber); // 42.0
	}
	
	public void main(String text) {

        DateTimeExtractor extractor = AiServices.create(DateTimeExtractor.class, chatLanguageModel);

        LocalDate date = extractor.extractDateFrom(text);
        System.out.println(date); // 1968-07-04

        LocalTime time = extractor.extractTimeFrom(text);
        System.out.println(time); // 23:45

        LocalDateTime dateTime = extractor.extractDateTimeFrom(text);
        System.out.println(dateTime); // 1968-07-04T23:45
    }

}
