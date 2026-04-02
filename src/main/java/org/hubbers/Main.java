package org.hubbers;

import org.hubbers.app.Bootstrap;
import org.hubbers.cli.HubbersCommand;
import org.hubbers.config.LogbackConfigurator;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        // Extract --repo option before creating RuntimeFacade
        String repoPath = extractRepoPath(args);
        LogbackConfigurator.configure(repoPath);
        int exitCode = new CommandLine(new HubbersCommand(Bootstrap.createRuntimeFacade(repoPath))).execute(args);
        System.exit(exitCode);
    }

    private static String extractRepoPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--repo".equals(args[i])) {
                return args[i + 1];
            }
        }
        // Check environment variable
        String envRepo = System.getenv("HUBBERS_REPO");
        if (envRepo != null && !envRepo.isBlank()) {
            return envRepo;
        }
        return "repo";
    }
}
