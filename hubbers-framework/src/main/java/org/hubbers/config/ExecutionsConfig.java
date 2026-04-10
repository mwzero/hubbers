package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExecutionsConfig {
    private String path = "./executions"; // default to ./executions relative to repo
    private Integer retentionDays;
    private Integer maxConcurrent;


}
