package com.ui.apps.utils;

import java.io.FileNotFoundException;
import java.io.FileReader;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class JsonFileHelper<T> {
	
	private final Class<T> type = null;
	
	public T getFile(String file) throws FileNotFoundException {
		Gson gson = new Gson();
		JsonReader reader = new JsonReader(new FileReader(file));
		return gson.fromJson(reader, type);
	}
}
