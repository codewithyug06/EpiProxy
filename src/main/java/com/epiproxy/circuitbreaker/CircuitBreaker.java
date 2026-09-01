package com.epiproxy.circuitbreaker;

import com.epiproxy.config.EpiProxyConfig;
import com.epiproxy.internal.RedisClientFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks and quarantines agents whose R0 exceeded the safety threshold.
 *
 * <p>Backed by Redis so quarantine state can be checked consistently under
 * horizontal scale-out. If Redis is unreachable, falls back to a local,
 * per-instance in-memory map with the same TTL semantics -- note this local
 * fallback is <b>not</b> shared across instances; it is a deliberate
 * availability-over-consistency tradeoff so a Redis outage degrades a single
 * instance's protection rather than taking the whole proxy down.
 */
public class CircuitBreaker implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private final double threshold;
    private final long quarantineTtlSeconds;
    private final Map<String, Instant> localFallback;
    private final AtomicBoolean redisUnavailableWarned = new AtomicBoolean(false);
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;

    public CircuitBreaker() {
        this(EpiProxyConfig.getInstance());
    }

    public CircuitBreaker(EpiProxyConfig config) {
        this.threshold = config.getCircuitBreakerThreshold();
        this.quarantineTtlSeconds = config.getQuarantineTtlSeconds();
        this.localFallback = new ConcurrentHashMap<>();

        try {
            this.redisClient = RedisClientFactory.create(
                    config.getRedisUrl(), config.getRedisConnectTimeoutMs(), config.getRedisCommandTimeoutMs());
            this.connection = redisClient.connect();
            this.syncCommands = connection.sync();
            logger.debug("Initialized CircuitBreaker with threshold {} and quarantineTtlSeconds {}",
                    threshold, quarantineTtlSeconds);
        } catch (RedisException | IllegalArgumentException e) {
            warnRedisUnavailable(e);
        }
    }

    public boolean isQuarantined(String agentName) {
        if (syncCommands != null) {
            try {
                return syncCommands.exists("quarantine:" + agentName) > 0;
            } catch (RedisException e) {
                warnRedisUnavailable(e);
            }
        }
        Instant expiry = localFallback.get(agentName);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            localFallback.remove(agentName, expiry);
            return false;
        }
        return true;
    }

    public boolean quarantine(String agentName, double r0) {
        if (r0 < threshold) {
            return false;
        }
        logger.info("Quarantining agent '{}' due to R0 ({}) >= {} for {}s", agentName, r0, threshold, quarantineTtlSeconds);
        if (syncCommands != null) {
            try {
                syncCommands.set("quarantine:" + agentName, "true", SetArgs.Builder.ex(quarantineTtlSeconds));
                return true;
            } catch (RedisException e) {
                warnRedisUnavailable(e);
            }
        }
        localFallback.put(agentName, Instant.now().plusSeconds(quarantineTtlSeconds));
        return true;
    }

    /** Manually clears an agent's quarantine, ahead of its TTL. Useful for operational overrides. */
    public void clearQuarantine(String agentName) {
        if (syncCommands != null) {
            try {
                syncCommands.del("quarantine:" + agentName);
            } catch (RedisException e) {
                warnRedisUnavailable(e);
            }
        }
        localFallback.remove(agentName);
    }

    private void warnRedisUnavailable(Exception e) {
        if (redisUnavailableWarned.compareAndSet(false, true)) {
            logger.warn("Redis unavailable for CircuitBreaker; using local in-memory fallback (per-instance only): {}",
                    e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null) connection.close();
        } finally {
            if (redisClient != null) redisClient.shutdown();
        }
    }
}
