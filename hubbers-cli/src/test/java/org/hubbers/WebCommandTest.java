package org.hubbers;

import org.hubbers.app.Bootstrap;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.cli.HubbersCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebCommand Tests")
class WebCommandTest {

    private static final int TEST_PORT = 17070;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private Thread serverThread;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        }
    }

    @Test
    @DisplayName("Should start web server on specified port and respond to health check")
    void testWebCommand_WithPort_StartsServerAndRespondsToHealth() throws Exception {
        // Given
        RuntimeFacade facade = Bootstrap.createRuntimeFacade();
        HubbersCommand command = new HubbersCommand(facade);
        CommandLine commandLine = new CommandLine(command);
        AtomicInteger exitCode = new AtomicInteger(-1);
        CountDownLatch started = new CountDownLatch(1);

        // When — launch the web command in a separate thread (it blocks with Thread.join)
        serverThread = new Thread(() -> {
            exitCode.set(commandLine.execute("web", "--port", String.valueOf(TEST_PORT)));
        }, "hubbers-web-test");
        serverThread.setDaemon(true);
        serverThread.start();

        // Wait for server to become ready by polling the health endpoint
        boolean ready = waitForServer("http://localhost:" + TEST_PORT + "/api/health", 30, 500);
        assertTrue(ready, "Web server should start and respond within timeout");

        // Then — verify health endpoint
        HttpRequest healthRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/health"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> healthResponse = httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, healthResponse.statusCode(), "Health endpoint should return 200");
        assertTrue(healthResponse.body().contains("\"status\""), "Health response should contain status field");
        assertTrue(healthResponse.body().contains("ok"), "Health status should be ok");
    }

    @Test
    @DisplayName("Should expose artifact listing endpoints")
    void testWebCommand_ArtifactEndpoints_ReturnSuccessfully() throws Exception {
        // Given
        RuntimeFacade facade = Bootstrap.createRuntimeFacade();
        HubbersCommand command = new HubbersCommand(facade);
        CommandLine commandLine = new CommandLine(command);

        serverThread = new Thread(() -> {
            commandLine.execute("web", "--port", String.valueOf(TEST_PORT + 1));
        }, "hubbers-web-test-artifacts");
        serverThread.setDaemon(true);
        serverThread.start();

        int port = TEST_PORT + 1;
        boolean ready = waitForServer("http://localhost:" + port + "/api/health", 30, 500);
        assertTrue(ready, "Web server should start and respond within timeout");

        // When & Then — verify all artifact listing endpoints
        String[] endpoints = {"/api/agents", "/api/tools", "/api/pipelines", "/api/skills"};
        for (String endpoint : endpoints) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + endpoint))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(),
                    endpoint + " should return 200");
            assertTrue(response.body().contains("items"),
                    endpoint + " response should contain items array");
        }
    }

    @Test
    @DisplayName("Should expose task info endpoint")
    void testWebCommand_TaskInfoEndpoint_ReturnsStatus() throws Exception {
        // Given
        RuntimeFacade facade = Bootstrap.createRuntimeFacade();
        HubbersCommand command = new HubbersCommand(facade);
        CommandLine commandLine = new CommandLine(command);

        serverThread = new Thread(() -> {
            commandLine.execute("web", "--port", String.valueOf(TEST_PORT + 2));
        }, "hubbers-web-test-taskinfo");
        serverThread.setDaemon(true);
        serverThread.start();

        int port = TEST_PORT + 2;
        boolean ready = waitForServer("http://localhost:" + port + "/api/health", 30, 500);
        assertTrue(ready, "Web server should start and respond within timeout");

        // When
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/task/info"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertEquals(200, response.statusCode(), "Task info endpoint should return 200");
        assertTrue(response.body().contains("available_tools"), "Response should list available tools count");
    }

    @Test
    @DisplayName("Should show web help with --help flag")
    void testWebCommand_WithHelp_ShowsUsage() {
        // Given
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);

        // When
        int exitCode = commandLine.execute("web", "--help");

        // Then
        assertEquals(0, exitCode, "Web --help should return exit code 0");
        String output = outContent.toString();
        assertTrue(output.contains("--port"), "Help should mention --port option");
        assertTrue(output.contains("web"), "Help should mention web command");
    }

    /**
     * Polls the given URL until it returns HTTP 200 or the maximum number of attempts is reached.
     *
     * @param url         the URL to poll
     * @param maxAttempts maximum number of attempts
     * @param intervalMs  milliseconds between attempts
     * @return true if the server responded with 200 within the allowed attempts
     */
    private boolean waitForServer(String url, int maxAttempts, long intervalMs) {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (Exception ignored) {
                // Server not ready yet
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
