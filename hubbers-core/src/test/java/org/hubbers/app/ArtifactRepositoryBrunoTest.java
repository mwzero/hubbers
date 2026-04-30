package org.hubbers.app;

import org.hubbers.manifest.tool.ToolManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArtifactRepository Bruno Discovery Tests")
class ArtifactRepositoryBrunoTest {

    @Test
    @DisplayName("Should discover Bruno request files as generated tools")
    void testListTools_WithBrunoCollection_ReturnsGeneratedToolName() throws URISyntaxException {
        ArtifactRepository repository = new ArtifactRepository(testRepoPath());

        assertTrue(repository.listTools().contains("bruno.hubbers-api.artifact-discovery.get-manifest"),
                "Generated Bruno tool should be listed alongside filesystem tools");
    }

    @Test
    @DisplayName("Should translate Bruno request into HTTP tool manifest")
    void testLoadTool_WithBrunoRequest_ReturnsGeneratedHttpManifest() throws URISyntaxException {
        ArtifactRepository repository = new ArtifactRepository(testRepoPath());

        ToolManifest manifest = repository.loadTool("bruno.hubbers-api.artifact-discovery.get-manifest");

        assertEquals("http", manifest.getType(), "Generated Bruno tool should use the HTTP driver");
        assertEquals("Get Manifest", manifest.getTool().getDescription(), "Description should come from Bruno metadata");
        assertEquals("GET", manifest.getConfig().get("method"), "HTTP method should be preserved");
        assertEquals("{{baseUrl}}/api/manifest/:type/:name", manifest.getConfig().get("base_url"),
                "Request URL should be preserved in config");

        @SuppressWarnings("unchecked")
        Map<String, String> pathParams = (Map<String, String>) manifest.getConfig().get("path_params");
        assertEquals("agents", pathParams.get("type"), "Path parameter defaults should be carried over");
        assertEquals("hello-world", pathParams.get("name"), "Path parameter defaults should be carried over");

        @SuppressWarnings("unchecked")
        Map<String, String> collectionVariables = (Map<String, String>) manifest.getConfig().get("variables");
        assertEquals("http://localhost:7070", collectionVariables.get("baseUrl"),
                "Collection variables should be available to the generated tool");

        assertNotNull(manifest.getInput(), "Generated manifest should define input schema");
        assertNotNull(manifest.getInput().getSchema().getProperties().get("type"),
                "Path params should be exposed as input properties");
        assertNotNull(manifest.getInput().getSchema().getProperties().get("name"),
                "Path params should be exposed as input properties");
    }

    private Path testRepoPath() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("repo").toURI());
    }
}