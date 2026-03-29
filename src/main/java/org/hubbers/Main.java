package org.hubbers;

import org.hubbers.app.Bootstrap;
import org.hubbers.cli.HubbersCommand;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new HubbersCommand(Bootstrap.createRuntimeFacade())).execute(args);
        System.exit(exitCode);
    }
}
