package org.hubbers.manifest.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Instructions {
    @JsonProperty("system_prompt")
    private String systemPrompt;
}
