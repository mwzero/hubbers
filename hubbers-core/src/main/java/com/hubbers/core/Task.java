package com.hubbers.core;

import java.util.Map;

import com.hubbers.core.model.AgentResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString(exclude = {"agent"})
public class Task {
	
	String description;
	String expectedOutput;
	
	Agent agent;
	
	
	public AgentResponse execute(Map<String, String> inputs) {
		
		return this.getAgent().execute(inputs, description);
	}
}
