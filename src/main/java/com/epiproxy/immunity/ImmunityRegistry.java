package com.epiproxy.immunity;

import com.epiproxy.config.EpiProxyConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ImmunityRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ImmunityRegistry.class);

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;
    private final String channel = "immunity_broadcast";

    public ImmunityRegistry() {
        this(EpiProxyConfig.getInstance().getRedisUrl());
    }

    public ImmunityRegistry(String redisUrl) {
        try {
            this.redisClient = RedisClient.create(redisUrl);
            this.connection = redisClient.connect();
            this.syncCommands = connection.sync();
            logger.debug("Initialized ImmunityRegistry with redis url: {}", redisUrl);
        } catch (RedisException | IllegalArgumentException e) {
            logger.error("Failed to connect to Redis for ImmunityRegistry.", e);
        }
    }

    public String computeHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
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
        if (syncCommands != null) {
            try {
                syncCommands.publish(channel, payloadSignature);
                syncCommands.sadd("immune_signatures", payloadSignature);
                logger.info("Broadcasted immune signature: {}", payloadSignature);
            } catch (RedisException e) {
                logger.warn("Redis connection error in broadcast: {}", e.getMessage());
            }
        }
    }

    public boolean checkImmune(String payloadSignature) {
        if (syncCommands != null) {
            try {
                return syncCommands.sismember("immune_signatures", payloadSignature);
            } catch (RedisException e) {
                logger.warn("Redis connection error in checkImmune: {}", e.getMessage());
            }
        }
        return false;
    }
}
