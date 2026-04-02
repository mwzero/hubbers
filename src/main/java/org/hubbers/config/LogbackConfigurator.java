package org.hubbers.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogbackConfigurator {
    
    public static void configure() {
        configure("repo");
    }
    
    public static void configure(String repoPath) {
        Path logbackPath = Paths.get(repoPath, "logback.xml");
        
        if (!Files.exists(logbackPath)) {
            System.err.println("Warning: logback.xml not found at " + logbackPath.toAbsolutePath());
            return;
        }
        
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(logbackPath.toFile());
        } catch (JoranException e) {
            System.err.println("Error configuring logback: " + e.getMessage());
        }
    }
}
