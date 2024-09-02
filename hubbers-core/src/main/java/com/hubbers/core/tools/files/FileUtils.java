package com.hubbers.core.tools.files;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileUtils {
	
	@Value("${file.path=c:\\temp}")
	private String filePath;
	
	public UUID write(String text) throws FileNotFoundException {
		
		UUID uuid = UUID.randomUUID();
		
		String fileName = String.format("%s/%s", filePath, uuid.toString());
		try (PrintWriter out = new PrintWriter(fileName)) {
		    out.println(text);
		}
		
		return uuid;
		
	}
}
