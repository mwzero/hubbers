package com.hubbers.core.tools.java;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.hubbers.core.model.AgentResponse;
import com.hubbers.utils.TestUtils;

public class TestExtractor {
	
	@Test
	@Disabled
	public void abc() throws IOException, URISyntaxException {
		
		String text = TestUtils.getFileString("./agent/response/output-1.txt");
		AgentResponse response = JavaCodeExtractor.extract(text);
		
		System.out.println(response.getResponse());
		
	}
	
	@Test
	public void compile() throws IOException, URISyntaxException {
		String javaCode = TestUtils.getFileString("./agent/response/output-1.txt");
		
		CompileJavaSource.process(javaCode);
		
	}

}
