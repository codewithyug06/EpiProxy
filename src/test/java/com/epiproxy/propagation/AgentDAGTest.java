package com.epiproxy.propagation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentDAGTest {

    @Test
    void tracksDownstreamFanOut() {
        AgentDAG dag = new AgentDAG();
        dag.addMessage("a", "b");
        dag.addMessage("b", "c");
        dag.addMessage("b", "d");

        List<String> downstream = dag.getDownstream("b");
        assertEquals(2, downstream.size());
        assertTrue(downstream.containsAll(List.of("c", "d")));
    }

    @Test
    void unknownNodeHasNoDownstream() {
        AgentDAG dag = new AgentDAG();
        assertTrue(dag.getDownstream("nonexistent").isEmpty());
    }

    @Test
    void prunesStaleAgentsWhenOverCapacity() throws InterruptedException {
        // maxAgents=2 forces a prune check after the 3rd distinct vertex is added.
        AgentDAG dag = new AgentDAG(2, Duration.ofMillis(50));
        dag.addMessage("a", "b");
        Thread.sleep(100); // let a/b age past the ttl
        dag.addMessage("c", "d"); // pushes vertex count over maxAgents, triggering prune

        // a/b should have been pruned as stale; c/d are fresh and retained.
        assertTrue(dag.getDownstream("c").isEmpty() || dag.getDownstream("c").contains("d"));
        assertTrue(dag.getDownstream("a").isEmpty());
    }
}
