package com.epiproxy.immunity;

import com.epiproxy.config.EpiProxyConfig;
import com.epiproxy.internal.RedisClientFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis-backed registry of "immune" payload signatures, broadcast via pub/sub
 * so every proxy instance in the swarm rejects a payload the moment any one
 * instance flags it. The signature set is bounded by TTL and a max size to
 * prevent unbounded growth in long-running deployments.
 *
 * <p>If Redis is unreachable, checks fall back to a local, per-instance,
 * TTL'd cache -- so a Redis outage degrades to per-instance-only immunity
 * rather than silently disabling immunity checks entirely.
 */
public class ImmunityRegistry implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ImmunityRegistry.class);
    private static final String SIGNATURE_SET_KEY = "immune_signatures";

    private final String channel = "immunity_broadcast";
    private final long signatureTtlSeconds;
    private final long signatureCap;
    private final Set<String> localFallback = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> localFallbackExpiry = new ConcurrentHashMap<>();
    private final AtomicBoolean redisUnavailableWarned = new AtomicBoolean(false);

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;

    public ImmunityRegistry() {
        this(EpiProxyConfig.getInstance());
    }

    public ImmunityRegistry(EpiProxyConfig config) {
        this.signatureTtlSeconds = config.getImmunitySignatureTtlSeconds();
        this.signatureCap = config.getImmunitySignatureCap();
        try {
            this.redisClient = RedisClientFactory.create(
                    config.getRedisUrl(), config.getRedisConnectTimeoutMs(), config.getRedisCommandTimeoutMs());
            this.connection = redisClient.connect();
            this.syncCommands = connection.sync();
            logger.debug("Initialized ImmunityRegistry (ttlSeconds={}, cap={})", signatureTtlSeconds, signatureCap);
        } catch (RedisException | IllegalArgumentException e) {
            warnRedisUnavailable(e);
        }
    }

    public String computeHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public void broadcast(String payloadSignature) {
        localFallback.add(payloadSignature);
        localFallbackExpiry.put(payloadSignature, Instant.now().plusSeconds(signatureTtlSeconds));

        if (syncCommands != null) {
            try {
                double score = Instant.now().toEpochMilli();
                syncCommands.publish(channel, payloadSignature);
                syncCommands.zadd(SIGNATURE_SET_KEY, score, payloadSignature);
                syncCommands.expire(SIGNATURE_SET_KEY, signatureTtlSeconds);
                trimToCapacity();
                logger.info("Broadcasted immune signature: {}", payloadSignature);
            } catch (RedisException e) {
                warnRedisUnavailable(e);
            }
        }
    }

    public boolean checkImmune(String payloadSignature) {
        if (syncCommands != null) {
            try {
                Double score = syncCommands.zscore(SIGNATURE_SET_KEY, payloadSignature);
                return score != null;
            } catch (RedisException e) {
                warnRedisUnavailable(e);
            }
        }
        Instant expiry = localFallbackExpiry.get(payloadSignature);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            localFallback.remove(payloadSignature);
            localFallbackExpiry.remove(payloadSignature);
            return false;
        }
        return localFallback.contains(payloadSignature);
    }

    private void trimToCapacity() {
        try {
            Long size = syncCommands.zcard(SIGNATURE_SET_KEY);
            if (size != null && size > signatureCap) {
                long excess = size - signatureCap;
                syncCommands.zremrangebyrank(SIGNATURE_SET_KEY, 0, excess - 1);
            }
        } catch (RedisException e) {
            warnRedisUnavailable(e);
        }
    }

    private void warnRedisUnavailable(Exception e) {
        if (redisUnavailableWarned.compareAndSet(false, true)) {
            logger.warn("Redis unavailable for ImmunityRegistry; using local in-memory fallback (per-instance only): {}",
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
