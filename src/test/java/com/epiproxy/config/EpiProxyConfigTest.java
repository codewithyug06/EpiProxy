package com.epiproxy.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EpiProxyConfigTest {

    @Test
    void builderAppliesExplicitValues() {
        EpiProxyConfig config = EpiProxyConfig.builder()
                .redisUrl("redis://example:6380")
                .heuristicThreshold(0.5)
                .circuitBreakerThreshold(2.0)
                .quarantineTtlSeconds(120)
                .build();

        assertEquals("redis://example:6380", config.getRedisUrl());
        assertEquals(0.5, config.getHeuristicThreshold());
        assertEquals(2.0, config.getCircuitBreakerThreshold());
        assertEquals(120, config.getQuarantineTtlSeconds());
    }

    @Test
    void builderClampsOutOfRangeThresholdToDefault() {
        EpiProxyConfig config = EpiProxyConfig.builder()
                .heuristicThreshold(5.0) // out of [0,1]
                .build();

        assertEquals(0.3, config.getHeuristicThreshold());
    }

    @Test
    void builderClampsNonPositiveTtlToDefault() {
        EpiProxyConfig config = EpiProxyConfig.builder()
                .quarantineTtlSeconds(-10)
                .build();

        assertEquals(3600, config.getQuarantineTtlSeconds());
    }

    @Test
    void builderRejectsMalformedTrustWeights() {
        EpiProxyConfig config = EpiProxyConfig.builder()
                .trustWeights(new double[]{1.0, 2.0}) // wrong length
                .build();

        assertArrayEquals(new double[]{0.0, 0.2, 0.5, 0.9}, config.getTrustWeights());
    }

    @Test
    void builderAcceptsValidTrustWeights() {
        double[] weights = {0.1, 0.2, 0.3, 0.4};
        EpiProxyConfig config = EpiProxyConfig.builder().trustWeights(weights).build();

        assertArrayEquals(weights, config.getTrustWeights());
    }

    @Test
    void defaultInstanceIsSingleton() {
        assertSame(EpiProxyConfig.getInstance(), EpiProxyConfig.getInstance());
    }
}
