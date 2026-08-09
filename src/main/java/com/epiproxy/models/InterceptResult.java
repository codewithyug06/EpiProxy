package com.epiproxy.models;

public record InterceptResult(
        boolean isSafe,
        double score,
        double r0,
        boolean quarantineTriggered,
        String reason
) {
}
