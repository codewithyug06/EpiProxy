package com.epiproxy.proxy;

import com.epiproxy.circuitbreaker.CircuitBreaker;
import com.epiproxy.classifier.HeuristicClassifier;
import com.epiproxy.config.EpiProxyConfig;
import com.epiproxy.exceptions.QuarantineException;
import com.epiproxy.exceptions.QuarantineException.QuarantineReason;
import com.epiproxy.immunity.ImmunityRegistry;
import com.epiproxy.models.AgentMessage;
import com.epiproxy.models.InterceptResult;
import com.epiproxy.propagation.AgentDAG;
import com.epiproxy.propagation.PropagationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Core middleware: intercepts agent-to-agent messages, scores them for
 * prompt-injection risk, computes the epidemiological R0 of allowing the
 * message through, and quarantines/blocks when the swarm-wide spread risk is
 * too high.
 *
 * <p>Owns Redis connections and a dedicated classifier thread pool; call
 * {@link #close()} (or use try-with-resources) to release them cleanly.
 */
public class SwarmProxy implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SwarmProxy.class);

    private final HeuristicClassifier classifier;
    private final AgentDAG dag;
    private final PropagationEngine propagationEngine;
    private final CircuitBreaker circuitBreaker;
    private final ImmunityRegistry immunityRegistry;
    private final Duration classifierTimeout;
    private final double timeoutFallbackScore;

    public SwarmProxy() {
        this(EpiProxyConfig.getInstance());
    }

    public SwarmProxy(EpiProxyConfig config) {
        logger.debug("Initializing SwarmProxy components...");
        this.classifier = new HeuristicClassifier(config, java.util.List.of());
        this.dag = new AgentDAG(config.getDagMaxAgents(), Duration.ofSeconds(config.getDagTtlSeconds()));
        this.propagationEngine = new PropagationEngine(this.dag, config);
        this.circuitBreaker = new CircuitBreaker(config);
        this.immunityRegistry = new ImmunityRegistry(config);
        this.classifierTimeout = Duration.ofSeconds((long) Math.ceil(config.getClassifierTimeoutSeconds()));
        this.timeoutFallbackScore = config.getTimeoutFallbackScore();
    }

    /** Dependency-injection constructor for tests: wires in pre-built (or mocked) components. */
    public SwarmProxy(HeuristicClassifier classifier, AgentDAG dag, PropagationEngine propagationEngine,
                       CircuitBreaker circuitBreaker, ImmunityRegistry immunityRegistry,
                       Duration classifierTimeout, double timeoutFallbackScore) {
        this.classifier = classifier;
        this.dag = dag;
        this.propagationEngine = propagationEngine;
        this.circuitBreaker = circuitBreaker;
        this.immunityRegistry = immunityRegistry;
        this.classifierTimeout = classifierTimeout;
        this.timeoutFallbackScore = timeoutFallbackScore;
    }

    /**
     * Synchronous interception of a message.
     * Blocks while calculating the ML score.
     */
    public InterceptResult intercept(AgentMessage message) {
        Objects.requireNonNull(message, "message must not be null");

        checkNotQuarantined(message.sourceAgent());
        String payloadHash = checkNotImmuneBlocked(message.payload());

        logger.debug("Scoring payload of length {}...", message.payload().length());
        double score = classifier.score(message.payload());

        return finishIntercept(message, score, payloadHash);
    }

    /**
     * Asynchronous interception.
     * Useful when the downstream node execution should be chained as a future.
     */
    public CompletableFuture<InterceptResult> aintercept(AgentMessage message) {
        Objects.requireNonNull(message, "message must not be null");

        try {
            checkNotQuarantined(message.sourceAgent());
        } catch (QuarantineException e) {
            return CompletableFuture.failedFuture(e);
        }

        String payloadHash;
        try {
            payloadHash = checkNotImmuneBlocked(message.payload());
        } catch (QuarantineException e) {
            return CompletableFuture.failedFuture(e);
        }

        String finalPayloadHash = payloadHash;
        return classifier.ascore(message.payload())
                .orTimeout(classifierTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    logger.warn("Classifier scoring timed out/failed for source={}; applying conservative fallback score {}",
                            message.sourceAgent(), timeoutFallbackScore, ex);
                    return timeoutFallbackScore;
                })
                .thenApply(score -> finishIntercept(message, score, finalPayloadHash));
    }

    /**
     * Wrap a generic functional execution with interception.
     */
    public <T> T wrapExecution(AgentMessage message, Supplier<T> executionBlock) {
        intercept(message); // Will throw QuarantineException if unsafe
        return executionBlock.get();
    }

    public AgentDAG getDag() {
        return dag;
    }

    private void checkNotQuarantined(String sourceAgent) {
        if (circuitBreaker.isQuarantined(sourceAgent)) {
            logger.warn("Intercept blocked: Source {} is quarantined.", sourceAgent);
            throw new QuarantineException(sourceAgent, QuarantineReason.AGENT_ALREADY_QUARANTINED,
                    "Agent " + sourceAgent + " is quarantined.");
        }
    }

    private String checkNotImmuneBlocked(String payload) {
        String payloadHash = immunityRegistry.computeHash(payload);
        if (immunityRegistry.checkImmune(payloadHash)) {
            logger.warn("Intercept blocked: Payload signature {} matches immunity registry.", payloadHash);
            throw new QuarantineException(null, QuarantineReason.IMMUNITY_SIGNATURE_BLOCKED,
                    "Message payload is blocked by immunity registry. Signature: " + payloadHash);
        }
        return payloadHash;
    }

    private InterceptResult finishIntercept(AgentMessage message, double score, String payloadHash) {
        double r0 = propagationEngine.computeR0(
                message.sourceAgent(),
                message.targetAgent(),
                score,
                message.trustTier()
        );
        logger.debug("Computed R0: {} (Score: {})", String.format("%.4f", r0), String.format("%.4f", score));

        boolean quarantined = circuitBreaker.quarantine(message.sourceAgent(), r0);

        if (quarantined) {
            logger.info("R0 threshold exceeded! Broadcasting immune signature {}", payloadHash);
            immunityRegistry.broadcast(payloadHash);
            throw new QuarantineException(message.sourceAgent(), QuarantineReason.R0_THRESHOLD_TRIPPED,
                    String.format("Message blocked. R0=%.2f", r0));
        }

        return InterceptResult.allowed(score, r0);
    }

    @Override
    public void close() {
        classifier.close();
        circuitBreaker.close();
        immunityRegistry.close();
    }
}
