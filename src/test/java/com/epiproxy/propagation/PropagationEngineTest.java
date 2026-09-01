package com.epiproxy.propagation;

import com.epiproxy.config.EpiProxyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropagationEngineTest {

    @Test
    void computesR0WithDefaultTrustWeights() {
        AgentDAG dag = new AgentDAG();
        PropagationEngine engine = new PropagationEngine(dag, EpiProxyConfig.builder().build());

        // trustTier 3 -> weight 0.9; target has no downstream yet -> fan-out floor of 1.
        double r0 = engine.computeR0("source", "target", 1.0, 3);
        assertEquals(0.9, r0, 1e-9);
    }

    @Test
    void scalesWithDownstreamFanOut() {
        AgentDAG dag = new AgentDAG();
        dag.addMessage("target", "child1");
        dag.addMessage("target", "child2");
        PropagationEngine engine = new PropagationEngine(dag, EpiProxyConfig.builder().build());

        double r0 = engine.computeR0("source", "target", 1.0, 3);
        assertEquals(1.8, r0, 1e-9); // 2 downstream * 1.0 * 0.9
    }

    @Test
    void respectsConfiguredTrustWeights() {
        AgentDAG dag = new AgentDAG();
        EpiProxyConfig config = EpiProxyConfig.builder()
                .trustWeights(new double[]{0.0, 0.1, 0.1, 0.1})
                .build();
        PropagationEngine engine = new PropagationEngine(dag, config);

        double r0 = engine.computeR0("source", "target", 1.0, 1);
        assertEquals(0.1, r0, 1e-9);
    }
}
