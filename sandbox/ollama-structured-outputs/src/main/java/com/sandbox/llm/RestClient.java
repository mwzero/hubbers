package com.sandbox.llm;

/*
 * prompt: You are a code assistant. Please write the Java code to invoke a REST endpoint using Java 17 and avoinding to using external dependencies like Spring. Only return the Java code adding comments directly to the code. Using the following template: <BEGIN_CODE>write here the code<END_CODE>"
 */

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.io.IOException;

public class RestClient {
	
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        String uri = "https://www.google.it"; // Replace with your endpoint URI
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Accept", "application/json") // Set headers if needed
                .GET() // Use POST, PUT, DELETE, etc., as per the REST method
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response status code: " + response.statusCode());
            HttpHeaders headers = response.headers();
            headers.map().forEach((k, v) -> System.out.println(k + ":" + v)); // Print headers for debugging
            String body = response.body();
            System.out.println("Response body: " + body);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}