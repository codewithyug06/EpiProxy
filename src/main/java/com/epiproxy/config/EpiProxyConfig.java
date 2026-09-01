package com.epiproxy.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Central configuration for EpiProxy. Values are loaded from a {@code .env}
 * file (if present) and/or the process environment, using the {@code EPIPROXY_}
 * prefix. Invalid values are logged and replaced with a safe default rather
 * than throwing, except for structurally malformed values that would make
 * the system unusable (currently none require a hard failure).
 *
 * <p>{@link #getInstance()} returns a process-wide singleton loaded from the
 * environment. For tests or callers that want isolated, explicit configuration,
 * use {@link #builder()} to construct a standalone instance that never touches
 * environment state.
 */
public final class EpiProxyConfig {
    private static final Logger logger = LoggerFactory.getLogger(EpiProxyConfig.class);
    private static volatile EpiProxyConfig instance;

    private final String redisUrl;
    private final String mlModelName;
    private final double heuristicThreshold;
    private final double circuitBreakerThreshold;
    private final String logLevel;
    private final double classifierTimeoutSeconds;
    private final long quarantineTtlSeconds;
    private final long immunitySignatureTtlSeconds;
    private final long immunitySignatureCap;
    private final long redisConnectTimeoutMs;
    private final long redisCommandTimeoutMs;
    private final long classifierCacheSize;
    private final int classifierExecutorThreads;
    private final long dagMaxAgents;
    private final long dagTtlSeconds;
    private final double[] trustWeights;
    private final double timeoutFallbackScore;

    private EpiProxyConfig(Builder b) {
        this.redisUrl = b.redisUrl;
        this.mlModelName = b.mlModelName;
        this.heuristicThreshold = b.heuristicThreshold;
        this.circuitBreakerThreshold = b.circuitBreakerThreshold;
        this.logLevel = b.logLevel;
        this.classifierTimeoutSeconds = b.classifierTimeoutSeconds;
        this.quarantineTtlSeconds = b.quarantineTtlSeconds;
        this.immunitySignatureTtlSeconds = b.immunitySignatureTtlSeconds;
        this.immunitySignatureCap = b.immunitySignatureCap;
        this.redisConnectTimeoutMs = b.redisConnectTimeoutMs;
        this.redisCommandTimeoutMs = b.redisCommandTimeoutMs;
        this.classifierCacheSize = b.classifierCacheSize;
        this.classifierExecutorThreads = b.classifierExecutorThreads;
        this.dagMaxAgents = b.dagMaxAgents;
        this.dagTtlSeconds = b.dagTtlSeconds;
        this.trustWeights = b.trustWeights;
        this.timeoutFallbackScore = b.timeoutFallbackScore;
    }

    /** Returns the process-wide singleton, lazily loaded from the environment on first access. */
    public static EpiProxyConfig getInstance() {
        EpiProxyConfig result = instance;
        if (result == null) {
            synchronized (EpiProxyConfig.class) {
                result = instance;
                if (result == null) {
                    instance = result = fromEnvironment();
                }
            }
        }
        return result;
    }

    /** Builds a config from the current process environment / {@code .env} file. */
    public static EpiProxyConfig fromEnvironment() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        Function<String, String> lookup = key -> {
            String value = dotenv.get(key);
            return value != null ? value : System.getenv(key);
        };
        return builder().loadFrom(lookup).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRedisUrl() { return redisUrl; }
    public String getMlModelName() { return mlModelName; }
    public double getHeuristicThreshold() { return heuristicThreshold; }
    public double getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public String getLogLevel() { return logLevel; }
    public double getClassifierTimeoutSeconds() { return classifierTimeoutSeconds; }
    public long getQuarantineTtlSeconds() { return quarantineTtlSeconds; }
    public long getImmunitySignatureTtlSeconds() { return immunitySignatureTtlSeconds; }
    public long getImmunitySignatureCap() { return immunitySignatureCap; }
    public long getRedisConnectTimeoutMs() { return redisConnectTimeoutMs; }
    public long getRedisCommandTimeoutMs() { return redisCommandTimeoutMs; }
    public long getClassifierCacheSize() { return classifierCacheSize; }
    public int getClassifierExecutorThreads() { return classifierExecutorThreads; }
    public long getDagMaxAgents() { return dagMaxAgents; }
    public long getDagTtlSeconds() { return dagTtlSeconds; }
    /** Returns a defensive copy of the 4 trust-tier weights (index = tier 0-3). */
    public double[] getTrustWeights() { return Arrays.copyOf(trustWeights, trustWeights.length); }
    public double getTimeoutFallbackScore() { return timeoutFallbackScore; }

    /** @deprecated use {@link #getClassifierTimeoutSeconds()}; kept for source compatibility. */
    @Deprecated
    public double getLanggraphTimeoutSeconds() { return classifierTimeoutSeconds; }

    public static final class Builder {
        private String redisUrl = "redis://localhost:6379";
        private String mlModelName = "all-MiniLM-L6-v2";
        private double heuristicThreshold = 0.3;
        private double circuitBreakerThreshold = 1.0;
        private String logLevel = "INFO";
        private double classifierTimeoutSeconds = 10.0;
        private long quarantineTtlSeconds = 3600;
        private long immunitySignatureTtlSeconds = 86_400;
        private long immunitySignatureCap = 100_000;
        private long redisConnectTimeoutMs = 2000;
        private long redisCommandTimeoutMs = 2000;
        private long classifierCacheSize = 10_000;
        private int classifierExecutorThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        private long dagMaxAgents = 50_000;
        private long dagTtlSeconds = 86_400;
        private double[] trustWeights = {0.0, 0.2, 0.5, 0.9};
        private double timeoutFallbackScore = 1.0;

        private Builder() {}

        public Builder redisUrl(String v) { this.redisUrl = v; return this; }
        public Builder mlModelName(String v) { this.mlModelName = v; return this; }
        public Builder heuristicThreshold(double v) { this.heuristicThreshold = validateUnit(v, "heuristicThreshold", heuristicThreshold); return this; }
        public Builder circuitBreakerThreshold(double v) { this.circuitBreakerThreshold = validatePositive(v, "circuitBreakerThreshold", circuitBreakerThreshold); return this; }
        public Builder logLevel(String v) { this.logLevel = v; return this; }
        public Builder classifierTimeoutSeconds(double v) { this.classifierTimeoutSeconds = validatePositive(v, "classifierTimeoutSeconds", classifierTimeoutSeconds); return this; }
        public Builder quarantineTtlSeconds(long v) { this.quarantineTtlSeconds = (long) validatePositive(v, "quarantineTtlSeconds", quarantineTtlSeconds); return this; }
        public Builder immunitySignatureTtlSeconds(long v) { this.immunitySignatureTtlSeconds = (long) validatePositive(v, "immunitySignatureTtlSeconds", immunitySignatureTtlSeconds); return this; }
        public Builder immunitySignatureCap(long v) { this.immunitySignatureCap = (long) validatePositive(v, "immunitySignatureCap", immunitySignatureCap); return this; }
        public Builder redisConnectTimeoutMs(long v) { this.redisConnectTimeoutMs = (long) validatePositive(v, "redisConnectTimeoutMs", redisConnectTimeoutMs); return this; }
        public Builder redisCommandTimeoutMs(long v) { this.redisCommandTimeoutMs = (long) validatePositive(v, "redisCommandTimeoutMs", redisCommandTimeoutMs); return this; }
        public Builder classifierCacheSize(long v) { this.classifierCacheSize = (long) validatePositive(v, "classifierCacheSize", classifierCacheSize); return this; }
        public Builder classifierExecutorThreads(int v) { this.classifierExecutorThreads = (int) validatePositive(v, "classifierExecutorThreads", classifierExecutorThreads); return this; }
        public Builder dagMaxAgents(long v) { this.dagMaxAgents = (long) validatePositive(v, "dagMaxAgents", dagMaxAgents); return this; }
        public Builder dagTtlSeconds(long v) { this.dagTtlSeconds = (long) validatePositive(v, "dagTtlSeconds", dagTtlSeconds); return this; }
        public Builder timeoutFallbackScore(double v) { this.timeoutFallbackScore = validateUnit(v, "timeoutFallbackScore", timeoutFallbackScore); return this; }

        public Builder trustWeights(double[] v) {
            if (v == null || v.length != 4 || Arrays.stream(v).anyMatch(x -> x < 0 || x > 2)) {
                logger.warn("Invalid trustWeights {} (need exactly 4 values in [0,2]); keeping default {}",
                        v == null ? "null" : Arrays.toString(v), Arrays.toString(trustWeights));
                return this;
            }
            this.trustWeights = v;
            return this;
        }

        /** Loads every key from the given lookup function, applying validation via the typed setters. */
        Builder loadFrom(Function<String, String> env) {
            redisUrl(getEnv(env, "EPIPROXY_REDIS_URL", redisUrl));
            mlModelName(getEnv(env, "EPIPROXY_ML_MODEL_NAME", mlModelName));
            heuristicThreshold(getEnvDouble(env, "EPIPROXY_HEURISTIC_THRESHOLD", heuristicThreshold));
            circuitBreakerThreshold(getEnvDouble(env, "EPIPROXY_CIRCUIT_BREAKER_THRESHOLD", circuitBreakerThreshold));
            logLevel(getEnv(env, "EPIPROXY_LOG_LEVEL", logLevel));

            // EPIPROXY_LANGGRAPH_TIMEOUT_SECONDS kept as a fallback alias for source/config compatibility.
            double timeoutDefault = getEnvDouble(env, "EPIPROXY_LANGGRAPH_TIMEOUT_SECONDS", classifierTimeoutSeconds);
            classifierTimeoutSeconds(getEnvDouble(env, "EPIPROXY_CLASSIFIER_TIMEOUT_SECONDS", timeoutDefault));

            quarantineTtlSeconds(getEnvLong(env, "EPIPROXY_QUARANTINE_TTL_SECONDS", quarantineTtlSeconds));
            immunitySignatureTtlSeconds(getEnvLong(env, "EPIPROXY_IMMUNITY_SIGNATURE_TTL_SECONDS", immunitySignatureTtlSeconds));
            immunitySignatureCap(getEnvLong(env, "EPIPROXY_IMMUNITY_SIGNATURE_CAP", immunitySignatureCap));
            redisConnectTimeoutMs(getEnvLong(env, "EPIPROXY_REDIS_CONNECT_TIMEOUT_MS", redisConnectTimeoutMs));
            redisCommandTimeoutMs(getEnvLong(env, "EPIPROXY_REDIS_COMMAND_TIMEOUT_MS", redisCommandTimeoutMs));
            classifierCacheSize(getEnvLong(env, "EPIPROXY_CLASSIFIER_CACHE_SIZE", classifierCacheSize));
            classifierExecutorThreads((int) getEnvLong(env, "EPIPROXY_CLASSIFIER_EXECUTOR_THREADS", classifierExecutorThreads));
            dagMaxAgents(getEnvLong(env, "EPIPROXY_DAG_MAX_AGENTS", dagMaxAgents));
            dagTtlSeconds(getEnvLong(env, "EPIPROXY_DAG_TTL_SECONDS", dagTtlSeconds));
            timeoutFallbackScore(getEnvDouble(env, "EPIPROXY_TIMEOUT_FALLBACK_SCORE", timeoutFallbackScore));

            String rawWeights = env.apply("EPIPROXY_TRUST_WEIGHTS");
            if (rawWeights != null && !rawWeights.isBlank()) {
                try {
                    double[] parsed = Arrays.stream(rawWeights.split(","))
                            .map(String::trim)
                            .mapToDouble(Double::parseDouble)
                            .toArray();
                    trustWeights(parsed);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid EPIPROXY_TRUST_WEIGHTS '{}': {}; keeping default {}",
                            rawWeights, e.getMessage(), Arrays.toString(trustWeights));
                }
            }
            return this;
        }

        public EpiProxyConfig build() {
            EpiProxyConfig config = new EpiProxyConfig(this);
            logger.info("Configuration loaded: redisUrl={}, mlModelName={}, heuristicThreshold={}, "
                            + "circuitBreakerThreshold={}, quarantineTtlSeconds={}, trustWeights={}",
                    config.redisUrl, config.mlModelName, config.heuristicThreshold,
                    config.circuitBreakerThreshold, config.quarantineTtlSeconds, Arrays.toString(config.trustWeights));
            return config;
        }

        private static String getEnv(Function<String, String> env, String key, String defaultValue) {
            String value = env.apply(key);
            return value != null ? value : defaultValue;
        }

        private static double getEnvDouble(Function<String, String> env, String key, double defaultValue) {
            String value = env.apply(key);
            if (value == null) return defaultValue;
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid double value for {}: '{}', using default {}", key, value, defaultValue);
                return defaultValue;
            }
        }

        private static long getEnvLong(Function<String, String> env, String key, long defaultValue) {
            String value = env.apply(key);
            if (value == null) return defaultValue;
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for {}: '{}', using default {}", key, value, defaultValue);
                return defaultValue;
            }
        }

        private static double validateUnit(double value, String name, double fallback) {
            if (value < 0 || value > 1) {
                logger.warn("{}={} out of range [0,1]; using {}", name, value, fallback);
                return fallback;
            }
            return value;
        }

        private static double validatePositive(double value, String name, double fallback) {
            if (value <= 0) {
                logger.warn("{}={} must be positive; using {}", name, value, fallback);
                return fallback;
            }
            return value;
        }
    }
}
