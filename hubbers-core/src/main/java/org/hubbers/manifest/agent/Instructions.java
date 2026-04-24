package org.hubbers.manifest.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Instructions {

    @JsonProperty("system_prompt")
    private String systemPrompt;

    @JsonProperty("user_prompt")
    private String userPrompt;
}
