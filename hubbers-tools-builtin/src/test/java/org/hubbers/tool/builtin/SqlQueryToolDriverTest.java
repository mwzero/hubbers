package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SqlQueryToolDriver}.
 */
class SqlQueryToolDriverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SqlQueryToolDriver driver;

    @BeforeEach
    void setUp() {
        driver = new SqlQueryToolDriver(mapper);
    }

    @Test
    void testType_ReturnsSqlQuery() {
        assertEquals("sql.query", driver.type());
    }

    @Test
    void testExecute_MissingConnectionUrl_ThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("query", "SELECT 1");

        assertThrows(IllegalArgumentException.class,
                () -> driver.execute(null, input));
    }

    @Test
    void testExecute_MissingQuery_ThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("connection_url", "jdbc:sqlite::memory:");

        assertThrows(IllegalArgumentException.class,
                () -> driver.execute(null, input));
    }

    @Test
    void testExecute_UnsafeInsertQuery_ThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("connection_url", "jdbc:sqlite::memory:");
        input.put("query", "INSERT INTO users VALUES (1, 'test')");

        assertThrows(IllegalArgumentException.class,
                () -> driver.execute(null, input));
    }

    @Test
    void testExecute_UnsafeDropQuery_ThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("connection_url", "jdbc:sqlite::memory:");
        input.put("query", "DROP TABLE users");

        assertThrows(IllegalArgumentException.class,
                () -> driver.execute(null, input));
    }

    @Test
    void testExecute_UnsafeDeleteInSelect_ThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("connection_url", "jdbc:sqlite::memory:");
        input.put("query", "SELECT 1; DELETE FROM users");

        assertThrows(IllegalArgumentException.class,
                () -> driver.execute(null, input));
    }

    @Test
    void testExecute_SelectQuery_ReturnsResults() {
        // SQLite in-memory DB — no external DB needed
        ObjectNode input = mapper.createObjectNode();
        input.put("connection_url", "jdbc:sqlite::memory:");
        input.put("query", "SELECT 1 AS num, 'hello' AS greeting");

        JsonNode result = driver.execute(null, input);

        assertTrue(result.has("columns"));
        assertTrue(result.has("rows"));
        assertEquals(1, result.get("row_count").asInt());

        JsonNode row = result.get("rows").get(0);
        assertEquals(1, row.get("num").asInt());
        assertEquals("hello", row.get("greeting").asText());
    }

    @Test
    void testExecute_WithParams_BindsCorrectly() {
        ObjectNode input = mapper.createObjectNode();
        input.put("connection_url", "jdbc:sqlite::memory:");
        input.put("query", "SELECT ? AS val");
        ArrayNode params = mapper.createArrayNode();
        params.add("test_value");
        input.set("params", params);

        JsonNode result = driver.execute(null, input);

        assertEquals(1, result.get("row_count").asInt());
        assertEquals("test_value", result.get("rows").get(0).get("val").asText());
    }

    @Test
    void testExecute_WithCteQuery_AllowsWithStatement() {
        ObjectNode input = mapper.createObjectNode();
        input.put("connection_url", "jdbc:sqlite::memory:");
        input.put("query", "WITH cte AS (SELECT 42 AS answer) SELECT * FROM cte");

        JsonNode result = driver.execute(null, input);

        assertEquals(1, result.get("row_count").asInt());
        assertEquals(42, result.get("rows").get(0).get("answer").asInt());
    }
}
