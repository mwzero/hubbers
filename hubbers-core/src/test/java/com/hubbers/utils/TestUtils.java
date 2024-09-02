package com.hubbers.utils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestUtils {

	public static String getFileString (String fileName) throws IOException, URISyntaxException {
		
		return new String(Files.readAllBytes(Paths.get(TestUtils.class.getClassLoader().getResource(fileName).toURI())));
	}

}
