package org.hubbers.tool.builtin;

import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolDriverContext;
import org.hubbers.tool.ToolDriverProvider;

import java.util.List;

/**
 * Registers the default built-in tool drivers shipped with Hubbers.
 */
public class BuiltinToolDriverProvider implements ToolDriverProvider {
    @Override
    public List<ToolDriver> createDrivers(ToolDriverContext context) {
        return List.of(
                new HttpToolDriver(context.httpClient(), context.jsonMapper()),
                new DockerToolDriver(context.jsonMapper()),
                new RssToolDriver(context.httpClient(), context.jsonMapper()),
                new FirecrawlToolDriver(context.jsonMapper(), context.appConfig()),
                new LuceneVectorContextToolDriver(context.jsonMapper()),
                new LuceneVectorUpsertToolDriver(context.jsonMapper()),
                new LuceneVectorSearchToolDriver(context.jsonMapper()),
                new LuceneKvToolDriver(context.jsonMapper()),
                new PinchtabBrowserToolDriver(context.httpClient(), context.jsonMapper()),
                new CsvWriteToolDriver(context.jsonMapper()),
                new CsvReadToolDriver(context.jsonMapper()),
                new FileOpsToolDriver(context.jsonMapper()),
                new ShellExecToolDriver(context.jsonMapper()),
                new ProcessManageToolDriver(context.jsonMapper()),
                new UserInputToolDriver(context.jsonMapper())
        );
    }
}
