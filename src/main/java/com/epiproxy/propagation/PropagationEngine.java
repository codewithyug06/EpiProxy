package com.epiproxy.propagation;

import com.epiproxy.config.EpiProxyConfig;

import java.util.List;

/**
 * Computes the epidemiological R0 (expected spread) of a message: the
 * classifier's maliciousness score, weighted by the target's trust tier and
 * multiplied by the target's downstream fan-out.
 */
public class PropagationEngine {
    private final AgentDAG dag;
    private final double[] trustWeights;

    public PropagationEngine(AgentDAG dag) {
        this(dag, EpiProxyConfig.getInstance());
    }

    public PropagationEngine(AgentDAG dag, EpiProxyConfig config) {
        this.dag = dag;
        this.trustWeights = config.getTrustWeights();
    }

    public double computeR0(String source, String target, double classifierScore, int trustTier) {
        this.dag.addMessage(source, target);

        List<String> downstreamNodes = this.dag.getDownstream(target);

        double weight = (trustTier >= 0 && trustTier < trustWeights.length) ? trustWeights[trustTier] : 0.5;
        double pAccept = classifierScore * weight;

        // If there are no downstream nodes yet, assume at least 1 potential downstream hop in worst case
        double expectedSpread = Math.max(1, downstreamNodes.size()) * pAccept;

        return expectedSpread;
    }
}
