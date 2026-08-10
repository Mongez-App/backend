package com.smartstudy.planning.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDecision(
    String action,
    String tool,
    Map<String, String> arguments
) {}
