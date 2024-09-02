package com.hubbers.core.tools.java;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hubbers.core.model.AgentResponse;

public class JavaCodeExtractor {
	
	static String startDelimiter ="```java";
    static String endDelimiter = "```";
    
	static String regex = startDelimiter + "(.*?)" + endDelimiter;
	
	public static AgentResponse extract(String response) {
		
		Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        
        AgentResponse result = new AgentResponse();
        
        while (matcher.find()) {
        	result.setResponse(matcher.group(1));
        }
        
        return result;

	}

}
