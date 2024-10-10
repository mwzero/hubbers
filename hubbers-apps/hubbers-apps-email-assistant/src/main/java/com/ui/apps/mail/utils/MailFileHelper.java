package com.ui.apps.mail.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

public class MailFileHelper {
	
	public final static String METADATA_FILE = "metadata.json";
	public final static String CONTENT_FILE = "content.txt";
	
	public static Map<String, String> getMetaDataFile(String rootPath) throws FileNotFoundException {
		Gson gson = new GsonBuilder().create();
		JsonReader reader = new JsonReader(new FileReader(rootPath + File.separator + METADATA_FILE));
		Map<String, String> metadata = gson.fromJson(reader, Map.class);
		
		return metadata;
	}
	
	public static String getContent(String rootPath) throws IOException {
		File file = new File(rootPath + File.separator + CONTENT_FILE);
		String content = new String(Files.readAllBytes(file.toPath()));
		return content;
	}
	
	public static void setMetaDataFile(String rootPath, Map<String, String> metadata) throws IOException {
		try (Writer writer = new FileWriter(rootPath + File.separator + METADATA_FILE)) {
			Gson gson = new GsonBuilder().create();
		    gson.toJson(metadata, writer);
		}
	}
	
	public static void setContentFile(String rootPath, String content) throws IOException {
		String fileContent = rootPath + File.separator + CONTENT_FILE;
		try (Writer out = new FileWriter(fileContent)) {
			out.write(content);
		}
	}
	
	
	
	
	public static boolean isContent(String pathName) {
		return pathName.contains(CONTENT_FILE);
	}
	
	public static boolean isMetaData(String pathName) {
		return pathName.contains(METADATA_FILE);
	}

}
