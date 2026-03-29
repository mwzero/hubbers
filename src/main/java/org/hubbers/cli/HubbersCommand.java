package org.hubbers.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.util.JacksonFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "hubbers", mixinStandardHelpOptions = true,
        subcommands = {HubbersCommand.ListCommand.class, HubbersCommand.AgentCommand.class,
                HubbersCommand.ToolCommand.class, HubbersCommand.PipelineCommand.class})
public class HubbersCommand implements Callable<Integer> {
    final RuntimeFacade runtimeFacade;

    public HubbersCommand(RuntimeFacade runtimeFacade) {
        this.runtimeFacade = runtimeFacade;
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @CommandLine.Command(name = "list", subcommands = {ListAgents.class, ListTools.class, ListPipelines.class})
    static class ListCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { CommandLine.usage(this, System.out); return 0; }
    }

    @CommandLine.Command(name = "agents")
    static class ListAgents implements Callable<Integer> {
        @CommandLine.ParentCommand ListCommand parent;
        @Override public Integer call() { return printList(parent.root.runtimeFacade.listAgents()); }
    }

    @CommandLine.Command(name = "tools")
    static class ListTools implements Callable<Integer> {
        @CommandLine.ParentCommand ListCommand parent;
        @Override public Integer call() { return printList(parent.root.runtimeFacade.listTools()); }
    }

    @CommandLine.Command(name = "pipelines")
    static class ListPipelines implements Callable<Integer> {
        @CommandLine.ParentCommand ListCommand parent;
        @Override public Integer call() { return printList(parent.root.runtimeFacade.listPipelines()); }
    }

    @CommandLine.Command(name = "agent", subcommands = AgentRun.class)
    static class AgentCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { return 0; }
    }

    @CommandLine.Command(name = "run")
    static class AgentRun implements Callable<Integer> {
        @CommandLine.ParentCommand AgentCommand parent;
        @CommandLine.Parameters(index = "0") String name;
        @CommandLine.Option(names = "--input", required = true) File input;
        @Override public Integer call() { return run(parent.root.runtimeFacade, name, input, Mode.AGENT); }
    }

    @CommandLine.Command(name = "tool", subcommands = ToolRun.class)
    static class ToolCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { return 0; }
    }

    @CommandLine.Command(name = "run")
    static class ToolRun implements Callable<Integer> {
        @CommandLine.ParentCommand ToolCommand parent;
        @CommandLine.Parameters(index = "0") String name;
        @CommandLine.Option(names = "--input", required = true) File input;
        @Override public Integer call() { return run(parent.root.runtimeFacade, name, input, Mode.TOOL); }
    }

    @CommandLine.Command(name = "pipeline", subcommands = PipelineRun.class)
    static class PipelineCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { return 0; }
    }

    @CommandLine.Command(name = "run")
    static class PipelineRun implements Callable<Integer> {
        @CommandLine.ParentCommand PipelineCommand parent;
        @CommandLine.Parameters(index = "0") String name;
        @CommandLine.Option(names = "--input", required = true) File input;
        @Override public Integer call() { return run(parent.root.runtimeFacade, name, input, Mode.PIPELINE); }
    }

    private enum Mode { AGENT, TOOL, PIPELINE }

    private static Integer run(RuntimeFacade facade, String name, File inputFile, Mode mode) {
        ObjectMapper mapper = JacksonFactory.jsonMapper();
        try {
            JsonNode input = mapper.readTree(Files.readString(inputFile.toPath()));
            RunResult result = switch (mode) {
                case AGENT -> facade.runAgent(name, input);
                case TOOL -> facade.runTool(name, input);
                case PIPELINE -> facade.runPipeline(name, input);
            };
            if (result.getStatus() == ExecutionStatus.SUCCESS) {
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.getOutput()));
                return 0;
            }
            System.err.println(result.getError());
            return 1;
        } catch (IOException e) {
            System.err.println("Input read/parse failed: " + e.getMessage());
            return 1;
        }
    }

    private static Integer printList(List<String> values) {
        values.forEach(System.out::println);
        return 0;
    }
}
