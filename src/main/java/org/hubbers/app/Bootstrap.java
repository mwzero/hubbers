package org.hubbers.app;

import org.hubbers.agent.AgentExecutor;
import org.hubbers.agent.AgentPromptBuilder;
import org.hubbers.artifact.ArtifactScanner;
import org.hubbers.artifact.LocalArtifactRepository;
import org.hubbers.config.OllamaConfig;
import org.hubbers.config.ConfigLoader;
import org.hubbers.model.ModelProviderRegistry;
import org.hubbers.model.OllamaModelProvider;
import org.hubbers.model.OpenAiModelProvider;
import org.hubbers.pipeline.InputMapper;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.tool.DockerToolDriver;
import org.hubbers.tool.HttpToolDriver;
import org.hubbers.tool.CsvReadToolDriver;
import org.hubbers.tool.CsvWriteToolDriver;
import org.hubbers.tool.LuceneKvToolDriver;
import org.hubbers.tool.LuceneVectorContextToolDriver;
import org.hubbers.tool.LuceneVectorSearchToolDriver;
import org.hubbers.tool.LuceneVectorUpsertToolDriver;
import org.hubbers.tool.PinchtabBrowserToolDriver;
import org.hubbers.tool.RssToolDriver;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.hubbers.validation.SchemaValidator;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;

public class Bootstrap {

    public static RuntimeFacade createRuntimeFacade() {
        var config = new ConfigLoader().load();
        var jsonMapper = JacksonFactory.jsonMapper();
        var httpClient = HttpClient.newHttpClient();

        var repository = new LocalArtifactRepository(Path.of(config.getRepoRoot()), new ArtifactScanner());

        var modelRegistry = new ModelProviderRegistry(List.of(
                new OpenAiModelProvider(httpClient, config.getOpenai()),
                new OllamaModelProvider(httpClient, config.getOllama() != null ? config.getOllama() : new OllamaConfig())
        ));

        var schemaValidator = new SchemaValidator();
        var agentExecutor = new AgentExecutor(modelRegistry, new AgentPromptBuilder(), schemaValidator, jsonMapper);

        var toolExecutor = new ToolExecutor(List.of(
                new HttpToolDriver(httpClient, jsonMapper),
                new DockerToolDriver(jsonMapper),
                new RssToolDriver(httpClient, jsonMapper),
                new LuceneVectorContextToolDriver(jsonMapper),
                new LuceneVectorUpsertToolDriver(jsonMapper),
                new LuceneVectorSearchToolDriver(jsonMapper),
                new LuceneKvToolDriver(jsonMapper),
                new PinchtabBrowserToolDriver(httpClient, jsonMapper),
                new CsvWriteToolDriver(jsonMapper),
                new CsvReadToolDriver(jsonMapper)
        ), schemaValidator);

        var pipelineExecutor = new PipelineExecutor(repository, agentExecutor, toolExecutor, new InputMapper(jsonMapper));

        return new RuntimeFacade(repository, agentExecutor, toolExecutor, pipelineExecutor, new ManifestValidator());
    }
}
