package com.epiproxy.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EpiProxyConfig {
    private static final Logger logger = LoggerFactory.getLogger(EpiProxyConfig.class);
    private static EpiProxyConfig instance;
    private final Dotenv dotenv;

    private String redisUrl;
    private String mlModelName;
    private double heuristicThreshold;
    private double circuitBreakerThreshold;
    private String logLevel;
    private double langgraphTimeoutSeconds;

    private EpiProxyConfig() {
        dotenv = Dotenv.configure().ignoreIfMissing().load();
        
        redisUrl = getEnv("EPIPROXY_REDIS_URL", "redis://localhost:6379");
        mlModelName = getEnv("EPIPROXY_ML_MODEL_NAME", "all-MiniLM-L6-v2");
        heuristicThreshold = getEnvDouble("EPIPROXY_HEURISTIC_THRESHOLD", 0.3);
        circuitBreakerThreshold = getEnvDouble("EPIPROXY_CIRCUIT_BREAKER_THRESHOLD", 1.0);
        logLevel = getEnv("EPIPROXY_LOG_LEVEL", "INFO");
        langgraphTimeoutSeconds = getEnvDouble("EPIPROXY_LANGGRAPH_TIMEOUT_SECONDS", 10.0);
        
        logger.info("Configuration loaded.");
    }

    public static synchronized EpiProxyConfig getInstance() {
        if (instance == null) {
            instance = new EpiProxyConfig();
        }
        return instance;
    }

    private String getEnv(String key, String defaultValue) {
        String value = dotenv.get(key);
        if (value == null) {
            value = System.getenv(key);
        }
        return value != null ? value : defaultValue;
    }

    private double getEnvDouble(String key, double defaultValue) {
        String value = getEnv(key, null);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid double value for {}: {}, using default {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    public String getRedisUrl() { return redisUrl; }
    public String getMlModelName() { return mlModelName; }
    public double getHeuristicThreshold() { return heuristicThreshold; }
    public double getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public String getLogLevel() { return logLevel; }
    public double getLanggraphTimeoutSeconds() { return langgraphTimeoutSeconds; }
}
