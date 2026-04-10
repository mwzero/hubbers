package org.hubbers.manifest.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Instructions {

    @JsonProperty("system_prompt")
    private String systemPrompt;
}
