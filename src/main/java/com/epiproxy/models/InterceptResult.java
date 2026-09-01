package com.epiproxy.models;

public record InterceptResult(
        boolean isSafe,
        double score,
        double r0,
        boolean quarantineTriggered,
        String reason
) {
    public static InterceptResult allowed(double score, double r0) {
        return new InterceptResult(true, score, r0, false, null);
    }

    public static InterceptResult blocked(double score, double r0, String reason) {
        return new InterceptResult(false, score, r0, true, reason);
    }
}
