package com.epiproxy.proxy;

import com.epiproxy.circuitbreaker.CircuitBreaker;
import com.epiproxy.classifier.HeuristicClassifier;
import com.epiproxy.exceptions.QuarantineException;
import com.epiproxy.immunity.ImmunityRegistry;
import com.epiproxy.models.AgentMessage;
import com.epiproxy.models.InterceptResult;
import com.epiproxy.propagation.AgentDAG;
import com.epiproxy.propagation.PropagationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SwarmProxy {
    private static final Logger logger = LoggerFactory.getLogger(SwarmProxy.class);

    private final HeuristicClassifier classifier;
    private final AgentDAG dag;
    private final PropagationEngine propagationEngine;
    private final CircuitBreaker circuitBreaker;
    private final ImmunityRegistry immunityRegistry;

    public SwarmProxy() {
        logger.debug("Initializing SwarmProxy components...");
        this.classifier = new HeuristicClassifier();
        this.dag = new AgentDAG();
        this.propagationEngine = new PropagationEngine(this.dag);
        this.circuitBreaker = new CircuitBreaker();
        this.immunityRegistry = new ImmunityRegistry();
    }

    /**
     * Synchronous interception of a message.
     * Blocks while calculating the ML score.
     */
    public InterceptResult intercept(AgentMessage message) {
        // 1. Check if source is already quarantined
        if (circuitBreaker.isQuarantined(message.sourceAgent())) {
            logger.warn("Intercept blocked: Source {} is quarantined.", message.sourceAgent());
            throw new QuarantineException("Agent " + message.sourceAgent() + " is quarantined.");
        }

        // 2. Check if payload signature is in immunity registry
        String payloadStr = message.payload();
        String payloadHash = immunityRegistry.computeHash(payloadStr);
        if (immunityRegistry.checkImmune(payloadHash)) {
            logger.warn("Intercept blocked: Payload signature {} matches immunity registry.", payloadHash);
            throw new QuarantineException("Message payload is blocked by immunity registry. Signature: " + payloadHash);
        }

        // 3. Score payload
        logger.debug("Scoring payload of length {}...", payloadStr.length());
        double score = classifier.score(payloadStr);

        // 4. Compute R0
        double r0 = propagationEngine.computeR0(
                message.sourceAgent(),
                message.targetAgent(),
                score,
                message.trustTier()
        );
        logger.debug("Computed R0: {} (Score: {})", String.format("%.4f", r0), String.format("%.4f", score));

        // 5. Trigger circuit breaker if needed
        boolean quarantined = circuitBreaker.quarantine(message.sourceAgent(), r0);

        if (quarantined) {
            logger.info("R0 threshold exceeded! Broadcasting immune signature {}", payloadHash);
            immunityRegistry.broadcast(payloadHash);
            throw new QuarantineException(String.format("Message blocked. R0=%.2f", r0));
        }

        return new InterceptResult(true, score, r0, false, null);
    }

    /**
     * Asynchronous interception.
     * Useful when the downstream node execution should be chained as a future.
     */
    public CompletableFuture<InterceptResult> aintercept(AgentMessage message) {
        if (circuitBreaker.isQuarantined(message.sourceAgent())) {
            logger.warn("Intercept blocked: Source {} is quarantined.", message.sourceAgent());
            return CompletableFuture.failedFuture(new QuarantineException("Agent " + message.sourceAgent() + " is quarantined."));
        }

        String payloadStr = message.payload();
        String payloadHash = immunityRegistry.computeHash(payloadStr);
        if (immunityRegistry.checkImmune(payloadHash)) {
            logger.warn("Intercept blocked: Payload signature {} matches immunity registry.", payloadHash);
            return CompletableFuture.failedFuture(new QuarantineException("Message payload is blocked by immunity registry. Signature: " + payloadHash));
        }

        return classifier.ascore(payloadStr).thenApply(score -> {
            double r0 = propagationEngine.computeR0(
                    message.sourceAgent(),
                    message.targetAgent(),
                    score,
                    message.trustTier()
            );

            boolean quarantined = circuitBreaker.quarantine(message.sourceAgent(), r0);

            if (quarantined) {
                logger.info("R0 threshold exceeded! Broadcasting immune signature {}", payloadHash);
                immunityRegistry.broadcast(payloadHash);
                throw new QuarantineException(String.format("Message blocked. R0=%.2f", r0));
            }

            return new InterceptResult(true, score, r0, false, null);
        });
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
}
