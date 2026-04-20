package org.hubbers.web;

import org.hubbers.app.Bootstrap;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.config.ConfigLoader;
import org.hubbers.config.LogbackConfigurator;
import org.hubbers.config.RepoPathResolver;
import org.hubbers.validation.ManifestValidator;

import java.nio.file.Path;

public class WebMain {
    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) {
        String repoPath = resolveRepoPath();
        LogbackConfigurator.configure(repoPath);
        int port = resolvePort(args);
        var appConfig = new ConfigLoader(repoPath).load();
        RuntimeFacade facade = Bootstrap.createRuntimeFacade(repoPath);

        ManifestFileService manifestFileService = new ManifestFileService(Path.of(appConfig.getRepoRoot()));

        new WebServer(facade, manifestFileService, new ManifestValidator(), repoPath).start(port);

        System.out.println("Hubbers Web UI available at http://localhost:" + port);
    }

    private static int resolvePort(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Integer.parseInt(args[0]);
        }
        String envPort = System.getenv("HUBBERS_WEB_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }
        return DEFAULT_PORT;
    }

    private static String resolveRepoPath() {
        return RepoPathResolver.resolve(null);
    }
}
