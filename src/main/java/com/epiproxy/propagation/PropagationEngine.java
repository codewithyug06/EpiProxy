package com.epiproxy.propagation;

import java.util.List;
import java.util.Map;

public class PropagationEngine {
    private final AgentDAG dag;
    private final Map<Integer, Double> trustWeights;

    public PropagationEngine(AgentDAG dag) {
        this.dag = dag;
        this.trustWeights = Map.of(
            0, 0.0, // Human, never accepts injection
            1, 0.2, // Orchestrator, low probability
            2, 0.5, // Sub-agent, medium probability
            3, 0.9  // External tool, high probability of being trusted blindly
        );
    }

    public double computeR0(String source, String target, double classifierScore, int trustTier) {
        this.dag.addMessage(source, target);
        
        List<String> downstreamNodes = this.dag.getDownstream(target);
        
        double pAccept = classifierScore * this.trustWeights.getOrDefault(trustTier, 0.5);
        
        // If there are no downstream nodes yet, assume at least 1 potential downstream hop in worst case
        double expectedSpread = Math.max(1, downstreamNodes.size()) * pAccept;
        
        return expectedSpread;
    }
}
