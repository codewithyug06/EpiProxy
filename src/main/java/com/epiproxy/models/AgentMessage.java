package com.epiproxy.models;

public record AgentMessage(
        String sourceAgent,
        String targetAgent,
        String payload,
        int trustTier
) {
    public AgentMessage {
        if (trustTier < 0 || trustTier > 3) {
            throw new IllegalArgumentException("Trust tier must be between 0 and 3.");
        }
    }

    public AgentMessage(String sourceAgent, String targetAgent, String payload) {
        this(sourceAgent, targetAgent, payload, 2); // Default to 2 (Sub-agent)
    }
}
