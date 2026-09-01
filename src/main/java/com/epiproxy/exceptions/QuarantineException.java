package com.epiproxy.exceptions;

/**
 * Raised when a message is blocked by EpiProxy. Carries a machine-readable
 * {@link QuarantineReason} so callers can branch on cause instead of parsing
 * {@link #getMessage()}.
 */
public class QuarantineException extends RuntimeException {

    public enum QuarantineReason {
        /** Source agent was already quarantined from a prior violation. */
        AGENT_ALREADY_QUARANTINED,
        /** The computed R0 for this message met or exceeded the circuit-breaker threshold. */
        R0_THRESHOLD_TRIPPED,
        /** The payload's signature matched a previously broadcast immunity signature. */
        IMMUNITY_SIGNATURE_BLOCKED
    }

    private final String agentId;
    private final QuarantineReason reason;

    public QuarantineException(String agentId, QuarantineReason reason, String message) {
        super(message);
        this.agentId = agentId;
        this.reason = reason;
    }

    public String getAgentId() {
        return agentId;
    }

    public QuarantineReason getReason() {
        return reason;
    }
}
