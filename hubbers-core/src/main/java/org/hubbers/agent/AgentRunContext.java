package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

import org.hubbers.manifest.agent.AgentManifest;

@Getter
@Setter
public class AgentRunContext {

    private AgentManifest manifest;
    private JsonNode input;

}
