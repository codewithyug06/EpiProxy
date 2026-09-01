package com.epiproxy.models;

import java.util.Objects;

public record AgentMessage(
        String sourceAgent,
        String targetAgent,
        String payload,
        int trustTier
) {
    public AgentMessage {
        if (sourceAgent == null || sourceAgent.isBlank()) {
            throw new IllegalArgumentException("sourceAgent must not be null or blank.");
        }
        if (targetAgent == null || targetAgent.isBlank()) {
            throw new IllegalArgumentException("targetAgent must not be null or blank.");
        }
        Objects.requireNonNull(payload, "payload must not be null (use an empty string instead).");
        if (trustTier < 0 || trustTier > 3) {
            throw new IllegalArgumentException("Trust tier must be between 0 and 3.");
        }
    }

    public AgentMessage(String sourceAgent, String targetAgent, String payload) {
        this(sourceAgent, targetAgent, payload, 2); // Default to 2 (Sub-agent)
    }
}
