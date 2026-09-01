package com.epiproxy.internal;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;

import java.time.Duration;

/**
 * Builds Lettuce {@link RedisClient} instances with consistent connect/command
 * timeouts and auto-reconnect behavior, shared by every Redis-backed component
 * so timeout configuration doesn't drift between them.
 */
public final class RedisClientFactory {

    private RedisClientFactory() {}

    public static RedisClient create(String redisUrl, long connectTimeoutMs, long commandTimeoutMs) {
        RedisURI uri = RedisURI.create(redisUrl);
        uri.setTimeout(Duration.ofMillis(connectTimeoutMs));

        RedisClient client = RedisClient.create(uri);
        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .timeoutOptions(TimeoutOptions.builder()
                        .fixedTimeout(Duration.ofMillis(commandTimeoutMs))
                        .build())
                .build());
        return client;
    }
}
