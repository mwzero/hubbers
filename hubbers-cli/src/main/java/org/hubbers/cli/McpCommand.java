package org.hubbers.cli;

import org.hubbers.app.RuntimeFacade;
import org.hubbers.mcp.McpPromptProvider;
import org.hubbers.mcp.McpRequestHandler;
import org.hubbers.mcp.McpResourceProvider;
import org.hubbers.mcp.McpStdioTransport;
import org.hubbers.mcp.McpToolProvider;
import org.hubbers.util.JacksonFactory;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * CLI subcommand that starts the MCP server in stdio mode.
 *
 * <p>This command is the entry point for external chat UIs like Claude Desktop
 * and VS Code. It reads JSON-RPC 2.0 requests from stdin and writes
 * responses to stdout.</p>
 *
 * <p>Example Claude Desktop configuration:
 * <pre>{@code
 * {
 *   "mcpServers": {
 *     "hubbers": {
 *       "command": "java",
 *       "args": ["-jar", "hubbers.jar", "mcp"]
 *     }
 *   }
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
@CommandLine.Command(
    mixinStandardHelpOptions = true,
    name = "mcp",
    description = "Start MCP server in stdio mode (for Claude Desktop, VS Code, etc.)"
)
public class McpCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    HubbersCommand root;

    @CommandLine.Option(
        names = "--config",
        description = "Print Claude Desktop MCP configuration snippet and exit"
    )
    boolean printConfig;

    @Override
    public Integer call() {
        if (printConfig) {
            return printMcpConfig();
        }
        return runStdioServer();
    }

    /**
     * Starts the MCP stdio transport, blocking until stdin is closed.
     *
     * @return exit code (0 = normal shutdown, 1 = error)
     */
    private int runStdioServer() {
        try {
            RuntimeFacade facade = root.runtimeFacade;
            var objectMapper = JacksonFactory.jsonMapper();
            var toolProvider = new McpToolProvider(facade.getArtifactRepository(), objectMapper);
            var promptProvider = new McpPromptProvider(facade.getArtifactRepository());
            var resourceProvider = new McpResourceProvider(facade.getArtifactRepository(),
                    java.nio.file.Path.of(root.repoPath));
            var handler = new McpRequestHandler(toolProvider, promptProvider, resourceProvider, facade, objectMapper);
            var transport = new McpStdioTransport(handler, objectMapper);

            // Redirect all logging to stderr so stdout stays clean for JSON-RPC
            System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");

            transport.run();
            return 0;
        } catch (Exception e) {
            System.err.println("MCP server failed: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Prints the Claude Desktop / VS Code MCP configuration snippet.
     *
     * @return exit code (always 0)
     */
    private int printMcpConfig() {
        String javaPath = ProcessHandle.current().info().command().orElse("java");
        String config = """
                {
                  "mcpServers": {
                    "hubbers": {
                      "command": "%s",
                      "args": ["-jar", "hubbers.jar", "mcp", "--repo", "%s"]
                    }
                  }
                }
                """.formatted(
                    javaPath.replace("\\", "\\\\"),
                    root.repoPath.replace("\\", "\\\\")
                );
        System.out.println(config);
        return 0;
    }
}
