package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.manifest.tool.ToolManifest;

public interface ToolDriver {
    String type();
    JsonNode execute(ToolManifest manifest, JsonNode input);
}
