package org.hubbers.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Utility class for building and executing HTTP requests.
 * 
 * <p>Provides fluent API for common HTTP operations with proper error handling
 * and JSON serialization. Reduces code duplication across tool drivers.</p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * HttpRequestBuilder builder = new HttpRequestBuilder(httpClient, jsonMapper);
 * JsonNode response = builder.post("https://api.example.com/data")
 *     .header("Authorization", "Bearer " + apiKey)
 *     .body(requestData)
 *     .executeForJson();
 * }</pre>
 * 
 * @since 0.1.0
 */
public class HttpRequestBuilder {
    
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private HttpRequest.Builder requestBuilder;
    private Object bodyObject;
    private String httpMethod; // Track the HTTP method for proper body attachment
    
    /**
     * Create a new HTTP request builder.
     * 
     * @param httpClient the HTTP client to use for requests
     * @param jsonMapper the JSON mapper for serialization/deserialization
     */
    public HttpRequestBuilder(HttpClient httpClient, ObjectMapper jsonMapper) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
    }
    
    /**
     * Start building a POST request.
     * 
     * @param url the target URL
     * @return this builder for method chaining
     */
    public HttpRequestBuilder post(String url) {
        this.requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30));
        this.httpMethod = "POST";
        return this;
    }
    
    /**
     * Start building a GET request.
     * 
     * @param url the target URL
     * @return this builder for method chaining
     */
    public HttpRequestBuilder get(String url) {
        this.requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .GET();
        this.httpMethod = "GET";
        return this;
    }
    
    /**
     * Start building a PUT request.
     * 
     * @param url the target URL
     * @return this builder for method chaining
     */
    public HttpRequestBuilder put(String url) {
        this.requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30));
        this.httpMethod = "PUT";
        return this;
    }
    
    /**
     * Start building a DELETE request.
     * 
     * @param url the target URL
     * @return this builder for method chaining
     */
    public HttpRequestBuilder delete(String url) {
        this.requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .DELETE();
        this.httpMethod = "DELETE";
        return this;
    }

    /**
     * Start building a request with an arbitrary HTTP method.
     *
     * @param method the HTTP method to use
     * @param url the target URL
     * @return this builder for method chaining
     */
    public HttpRequestBuilder method(String method, String url) {
        this.requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30));
        this.httpMethod = method.toUpperCase();
        return this;
    }
    
    /**
     * Add a header to the request.
     * 
     * @param name the header name
     * @param value the header value
     * @return this builder for method chaining
     */
    public HttpRequestBuilder header(String name, String value) {
        if (value != null) {
            requestBuilder.header(name, value);
        }
        return this;
    }
    
    /**
     * Set the request timeout.
     * 
     * @param duration the timeout duration
     * @return this builder for method chaining
     */
    public HttpRequestBuilder timeout(Duration duration) {
        requestBuilder.timeout(duration);
        return this;
    }
    
    /**
     * Set the request body (will be serialized to JSON).
     * 
     * @param body the body object to serialize
     * @return this builder for method chaining
     */
    public HttpRequestBuilder body(Object body) {
        this.bodyObject = body;
        return this;
    }
    
    /**
     * Set the request body as raw JSON string.
     * 
     * @param jsonBody the JSON string body
     * @return this builder for method chaining
     */
    public HttpRequestBuilder jsonBody(String jsonBody) {
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        return this;
    }

    /**
     * Set the request body as a raw string with an optional content type.
     *
     * @param rawBody the raw request body
     * @param contentType the content type header to apply
     * @return this builder for method chaining
     */
    public HttpRequestBuilder rawBody(String rawBody, String contentType) {
        this.bodyObject = null;
        if (contentType != null && !contentType.isBlank()) {
            requestBuilder.setHeader("Content-Type", contentType);
        }
        requestBuilder.method(httpMethod, HttpRequest.BodyPublishers.ofString(rawBody));
        return this;
    }
    
    /**
     * Execute the request and parse response as JSON.
     * 
     * @return the response parsed as JsonNode
     * @throws IOException if the request fails or response parsing fails
     */
    public JsonNode executeForJson() throws IOException {
        return executeForType(JsonNode.class);
    }
    
    /**
     * Execute the request and parse response as specified type.
     * 
     * @param <T> the response type
     * @param responseType the class of the response type
     * @return the parsed response
     * @throws IOException if the request fails or response parsing fails
     */
    public <T> T executeForType(Class<T> responseType) throws IOException {
        try {
            // Serialize body if present and method supports body
            if (bodyObject != null && ("POST".equals(httpMethod) || "PUT".equals(httpMethod))) {
                String jsonBody = jsonMapper.writeValueAsString(bodyObject);
                requestBuilder.method(httpMethod, HttpRequest.BodyPublishers.ofString(jsonBody));
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check for HTTP errors
            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                String errorBody = response.body();
                throw new IOException(
                    String.format("HTTP %d error: %s", statusCode, errorBody != null ? errorBody : "No error details")
                );
            }
            
            // Parse response
            return jsonMapper.readValue(response.body(), responseType);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
    
    /**
     * Execute the request and return raw response body.
     * 
     * @return the raw response body as string
     * @throws IOException if the request fails
     */
    public String executeForString() throws IOException {
        try {
            // Serialize body if present and method supports body
            if (bodyObject != null && ("POST".equals(httpMethod) || "PUT".equals(httpMethod))) {
                String jsonBody = jsonMapper.writeValueAsString(bodyObject);
                requestBuilder.method(httpMethod, HttpRequest.BodyPublishers.ofString(jsonBody));
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check for HTTP errors
            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                String errorBody = response.body();
                throw new IOException(
                    String.format("HTTP %d error: %s", statusCode, errorBody != null ? errorBody : "No error details")
                );
            }
            
            return response.body();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
    
    /**
     * Create a quick POST request with JSON body.
     * 
     * @param httpClient the HTTP client
     * @param jsonMapper the JSON mapper
     * @param url the target URL
     * @param body the request body
     * @return the response as JsonNode
     * @throws IOException if the request fails
     */
    public static JsonNode quickPost(HttpClient httpClient, ObjectMapper jsonMapper, String url, Object body) throws IOException {
        return new HttpRequestBuilder(httpClient, jsonMapper)
            .post(url)
            .body(body)
            .executeForJson();
    }
    
    /**
     * Create a quick GET request.
     * 
     * @param httpClient the HTTP client
     * @param jsonMapper the JSON mapper
     * @param url the target URL
     * @return the response as JsonNode
     * @throws IOException if the request fails
     */
    public static JsonNode quickGet(HttpClient httpClient, ObjectMapper jsonMapper, String url) throws IOException {
        return new HttpRequestBuilder(httpClient, jsonMapper)
            .get(url)
            .executeForJson();
    }
}
