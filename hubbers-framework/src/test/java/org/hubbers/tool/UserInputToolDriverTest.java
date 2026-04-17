package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserInputToolDriverTest {

    private UserInputToolDriver driver;
    private ObjectMapper mapper;
    private ToolManifest manifest;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        driver = new UserInputToolDriver(mapper);
        manifest = new ToolManifest();
    }

    @Test
    void testToolType() {
        assertEquals("user-interaction", driver.type(), "Driver type should be user-interaction");
    }

    @Test
    void testExecuteWithAllFields() {
        ObjectNode input = mapper.createObjectNode();
        input.put("prompt", "What is the RSS feed URL?");
        input.put("field_name", "rss_url");
        input.put("field_type", "url");
        input.put("placeholder", "https://example.com/rss.xml");

        JsonNode result = driver.execute(manifest, input);

        assertEquals("requires_user_input", result.path("status").asText(),
            "Status should indicate user input is required");
        assertEquals("What is the RSS feed URL?", result.path("prompt").asText());
        assertEquals("rss_url", result.path("field_name").asText());
        assertEquals("url", result.path("field_type").asText());
        assertEquals("https://example.com/rss.xml", result.path("placeholder").asText());
        assertFalse(result.path("message").asText().isEmpty(), "Should have a message");
        assertFalse(result.path("next_steps").asText().isEmpty(), "Should have next_steps");
    }

    @Test
    void testExecuteWithDefaults() {
        ObjectNode input = mapper.createObjectNode();

        JsonNode result = driver.execute(manifest, input);

        assertEquals("requires_user_input", result.path("status").asText());
        assertEquals("Please provide more information", result.path("prompt").asText(),
            "Should use default prompt");
        assertEquals("user_response", result.path("field_name").asText(),
            "Should use default field_name");
        assertEquals("text", result.path("field_type").asText(),
            "Should use default field_type");
        assertEquals("", result.path("placeholder").asText(),
            "Should use empty default placeholder");
    }

    @Test
    void testExecuteWithPartialFields() {
        ObjectNode input = mapper.createObjectNode();
        input.put("prompt", "Enter your name");

        JsonNode result = driver.execute(manifest, input);

        assertEquals("requires_user_input", result.path("status").asText());
        assertEquals("Enter your name", result.path("prompt").asText());
        assertEquals("user_response", result.path("field_name").asText(),
            "Non-provided fields should use defaults");
    }

    @Test
    void testMessageContainsPrompt() {
        ObjectNode input = mapper.createObjectNode();
        input.put("prompt", "Please confirm your email");

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("message").asText().contains("Please confirm your email"),
            "Message should include the prompt text");
        assertTrue(result.path("next_steps").asText().contains("Please confirm your email"),
            "Next steps should include the prompt text");
    }
}
