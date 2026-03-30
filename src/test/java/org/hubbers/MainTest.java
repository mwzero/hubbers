package org.hubbers;

import org.hubbers.app.Bootstrap;
import org.hubbers.cli.HubbersCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }


    @Test
    void testGenericCommand() {
        // Test generic tool run command
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("tool", "run", "browser.pinchtab", "--input", "{\"action\":\"navigate\",\"url\":\"https://example.com\"}");
        
        assertEquals(0, exitCode, "Tool run command should return exit code 0");
    }

    @Test
    void testCommandLineCreation() {
        // Test that CommandLine can be created with HubbersCommand
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        assertNotNull(commandLine, "CommandLine should be created successfully");
        assertNotNull(commandLine.getCommand(), "CommandLine should have a command");
    }

    @Test
    void testHelpOption() {
        // Test --help option
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("--help");
        
        assertEquals(0, exitCode, "Help option should return exit code 0");
        assertTrue(outContent.toString().contains("Usage:"), 
                "Help output should contain usage information");
    }

    @Test
    void testVersionOption() {
        // Test --version option
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("--version");
        
        assertEquals(0, exitCode, "Version option should return exit code 0");
    }

    @Test
    void testListAgentsCommand() {
        // Test list agents command
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("list", "agents");
        
        assertEquals(0, exitCode, "List agents command should return exit code 0");
    }

    @Test
    void testListToolsCommand() {
        // Test list tools command
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("list", "tools");
        
        assertEquals(0, exitCode, "List tools command should return exit code 0");
        String output = outContent.toString();
        assertTrue(output.contains("lucene.kv") || output.contains("browser.pinchtab"), 
                "List tools should include newly added tools");
    }

    @Test
    void testListPipelinesCommand() {
        // Test list pipelines command
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("list", "pipelines");
        
        assertEquals(0, exitCode, "List pipelines command should return exit code 0");
    }

    @Test
    void testInvalidCommand() {
        // Test invalid command
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("invalid-command");
        
        assertNotEquals(0, exitCode, "Invalid command should return non-zero exit code");
    }

    @Test
    void testNoArguments() {
        // Test running without arguments (should show help)
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute();
        
        assertEquals(0, exitCode, "No arguments should return exit code 0 (shows help)");
        assertTrue(outContent.toString().contains("Usage:"), 
                "No arguments should display usage information");
    }

    @Test
    void testAgentRunWithoutRequiredInput() {
        // Test agent run command without required input parameter
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("agent", "run", "text.summarizer");
        
        assertNotEquals(0, exitCode, "Agent run without input should return non-zero exit code");
        assertTrue(errContent.toString().contains("Missing required option") || 
                   errContent.toString().contains("--input"),
                "Error message should mention missing input parameter");
    }

    @Test
    void testToolRunWithoutRequiredInput() {
        // Test tool run command without required input parameter
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("tool", "run", "csv.read");
        
        assertNotEquals(0, exitCode, "Tool run without input should return non-zero exit code");
        assertTrue(errContent.toString().contains("Missing required option") || 
                   errContent.toString().contains("--input"),
                "Error message should mention missing input parameter");
    }

    @Test
    void testPipelineRunWithoutRequiredInput() {
        // Test pipeline run command without required input parameter
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("pipeline", "run", "pdf.summary");
        
        assertNotEquals(0, exitCode, "Pipeline run without input should return non-zero exit code");
        assertTrue(errContent.toString().contains("Missing required option") || 
                   errContent.toString().contains("--input"),
                "Error message should mention missing input parameter");
    }

    @Test
    void testRuntimeFacadeCreation() {
        // Test that Bootstrap.createRuntimeFacade() doesn't throw exceptions
        assertDoesNotThrow(() -> {
            var facade = Bootstrap.createRuntimeFacade();
            assertNotNull(facade, "RuntimeFacade should be created successfully");
        });
    }

    @Test
    void testCommandLineWithMultipleArguments() {
        // Test command line with multiple arguments
        HubbersCommand command = new HubbersCommand(Bootstrap.createRuntimeFacade());
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute("list", "agents", "--help");
        
        assertEquals(0, exitCode, "Command with --help should return exit code 0");
    }
}
