package com.epiproxy.circuitbreaker;

import com.epiproxy.config.EpiProxyConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreaker {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private final double threshold;
    private final Set<String> localFallback;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;

    public CircuitBreaker() {
        this(EpiProxyConfig.getInstance().getCircuitBreakerThreshold(), EpiProxyConfig.getInstance().getRedisUrl());
    }

    public CircuitBreaker(double threshold, String redisUrl) {
        this.threshold = threshold;
        this.localFallback = ConcurrentHashMap.newKeySet();
        
        try {
            this.redisClient = RedisClient.create(redisUrl);
            this.connection = redisClient.connect();
            this.syncCommands = connection.sync();
            logger.debug("Initialized CircuitBreaker with threshold {}", this.threshold);
        } catch (RedisException | IllegalArgumentException e) {
            logger.error("Failed to connect to Redis for CircuitBreaker. Using local fallback.", e);
        }
    }

    public boolean isQuarantined(String agentName) {
        if (syncCommands != null) {
            try {
                return syncCommands.exists("quarantine:" + agentName) > 0;
            } catch (RedisException e) {
                logger.warn("Redis connection error in isQuarantined: {}", e.getMessage());
            }
        }
        return localFallback.contains(agentName);
    }

    public boolean quarantine(String agentName, double r0) {
        if (r0 >= threshold) {
            logger.info("Quarantining agent '{}' due to R0 ({}) >= {}", agentName, r0, threshold);
            if (syncCommands != null) {
                try {
                    syncCommands.set("quarantine:" + agentName, "true");
                    return true;
                } catch (RedisException e) {
                    logger.warn("Redis connection error in quarantine: {}", e.getMessage());
                }
            }
            localFallback.add(agentName);
            return true;
        }
        return false;
    }
}
