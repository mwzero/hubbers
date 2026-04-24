package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.tool.ToolDriver;

import java.sql.*;
import java.util.regex.Pattern;

/**
 * Tool driver for executing read-only SQL queries via JDBC.
 *
 * <p>Supports any JDBC-compatible database via the {@code connection_url} input.
 * Only SELECT statements are allowed by default to prevent data mutation.</p>
 *
 * <p>Input fields:
 * <ul>
 *   <li>{@code connection_url} — JDBC connection string</li>
 *   <li>{@code query} — the SQL SELECT statement to execute</li>
 *   <li>{@code params} — optional array of positional parameters</li>
 *   <li>{@code max_rows} — optional maximum rows to return (default 1000)</li>
 * </ul>
 *
 * <p>Output: {@code { "columns": [...], "rows": [...], "row_count": N }}
 */
@Slf4j
@RequiredArgsConstructor
public class SqlQueryToolDriver implements ToolDriver {

    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final Pattern UNSAFE_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE);

    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "sql.query";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String connectionUrl = requireField(input, "connection_url");
        String query = requireField(input, "query");
        int maxRows = input.has("max_rows") ? input.get("max_rows").asInt(DEFAULT_MAX_ROWS) : DEFAULT_MAX_ROWS;

        // Security: reject non-SELECT statements
        validateQuery(query);

        log.debug("Executing SQL query against '{}': {}", maskConnectionUrl(connectionUrl), query);

        try (Connection conn = DriverManager.getConnection(connectionUrl);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setMaxRows(maxRows);

            // Bind positional parameters
            if (input.has("params") && input.get("params").isArray()) {
                int paramIndex = 1;
                for (JsonNode param : input.get("params")) {
                    if (param.isTextual()) {
                        stmt.setString(paramIndex, param.asText());
                    } else if (param.isInt()) {
                        stmt.setInt(paramIndex, param.asInt());
                    } else if (param.isLong()) {
                        stmt.setLong(paramIndex, param.asLong());
                    } else if (param.isDouble() || param.isFloat()) {
                        stmt.setDouble(paramIndex, param.asDouble());
                    } else if (param.isBoolean()) {
                        stmt.setBoolean(paramIndex, param.asBoolean());
                    } else if (param.isNull()) {
                        stmt.setNull(paramIndex, Types.NULL);
                    } else {
                        stmt.setString(paramIndex, param.asText());
                    }
                    paramIndex++;
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToJson(rs);
            }
        } catch (SQLException e) {
            log.error("SQL query failed: {}", e.getMessage());
            throw new IllegalStateException("SQL query failed: " + e.getMessage(), e);
        }
    }

    private void validateQuery(String query) {
        String trimmed = query.trim();
        if (!trimmed.toUpperCase().startsWith("SELECT")
                && !trimmed.toUpperCase().startsWith("WITH")) {
            throw new IllegalArgumentException(
                    "Only SELECT and WITH (CTE) queries are allowed. Got: " + trimmed.substring(0, Math.min(30, trimmed.length())));
        }
        if (UNSAFE_PATTERN.matcher(query).find()) {
            throw new IllegalArgumentException(
                    "Query contains disallowed SQL statement. Only read-only queries are permitted.");
        }
    }

    private JsonNode resultSetToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        ArrayNode columns = mapper.createArrayNode();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        ArrayNode rows = mapper.createArrayNode();
        while (rs.next()) {
            ObjectNode row = mapper.createObjectNode();
            for (int i = 1; i <= columnCount; i++) {
                String colName = meta.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value == null) {
                    row.putNull(colName);
                } else if (value instanceof Integer v) {
                    row.put(colName, v);
                } else if (value instanceof Long v) {
                    row.put(colName, v);
                } else if (value instanceof Double v) {
                    row.put(colName, v);
                } else if (value instanceof Float v) {
                    row.put(colName, v);
                } else if (value instanceof Boolean v) {
                    row.put(colName, v);
                } else {
                    row.put(colName, value.toString());
                }
            }
            rows.add(row);
        }

        ObjectNode output = mapper.createObjectNode();
        output.set("columns", columns);
        output.set("rows", rows);
        output.put("row_count", rows.size());
        return output;
    }

    private String requireField(JsonNode input, String fieldName) {
        JsonNode field = input.get(fieldName);
        if (field == null || field.isNull() || field.asText().isBlank()) {
            throw new IllegalArgumentException("Required field '" + fieldName + "' is missing or blank");
        }
        return field.asText();
    }

    private String maskConnectionUrl(String url) {
        // Mask password in JDBC URLs for logging
        return url.replaceAll("(?i)(password=)[^;&]+", "$1***");
    }
}
