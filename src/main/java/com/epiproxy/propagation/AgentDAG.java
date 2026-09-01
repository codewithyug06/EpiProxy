package com.epiproxy.propagation;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tracks agent-to-agent message topology as a directed weighted graph, used
 * to estimate downstream fan-out for the R0 calculation.
 *
 * <p>Bounded via {@code maxAgents}/{@code ttl}: once the vertex count exceeds
 * {@code maxAgents}, vertices untouched for longer than {@code ttl} are
 * pruned, so a long-running swarm with a rotating cast of agents doesn't leak
 * memory indefinitely.
 */
public class AgentDAG {
    private static final Logger logger = LoggerFactory.getLogger(AgentDAG.class);

    private final DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph;
    private final ReentrantReadWriteLock lock;
    private final Map<String, Instant> lastSeen;
    private final long maxAgents;
    private final Duration ttl;

    public AgentDAG() {
        this(50_000, Duration.ofDays(1));
    }

    public AgentDAG(long maxAgents, Duration ttl) {
        this.graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        this.lock = new ReentrantReadWriteLock();
        this.lastSeen = new ConcurrentHashMap<>();
        this.maxAgents = maxAgents;
        this.ttl = ttl;
    }

    public void addMessage(String source, String target) {
        lock.writeLock().lock();
        try {
            if (!graph.containsVertex(source)) {
                graph.addVertex(source);
            }
            if (!graph.containsVertex(target)) {
                graph.addVertex(target);
            }
            Instant now = Instant.now();
            lastSeen.put(source, now);
            lastSeen.put(target, now);

            DefaultWeightedEdge edge = graph.getEdge(source, target);
            if (edge != null) {
                double weight = graph.getEdgeWeight(edge);
                graph.setEdgeWeight(edge, weight + 1.0);
            } else {
                Graphs.addEdge(graph, source, target, 1.0);
            }

            if (graph.vertexSet().size() > maxAgents) {
                pruneStale(ttl);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> getDownstream(String node) {
        lock.readLock().lock();
        try {
            if (!graph.containsVertex(node)) {
                return List.of();
            }
            return Graphs.successorListOf(graph, node);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Removes vertices (and their edges) not touched within {@code maxAge}. Caller must hold the write lock. */
    private void pruneStale(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        List<String> stale = lastSeen.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();
        for (String agent : stale) {
            graph.removeVertex(agent);
            lastSeen.remove(agent);
        }
        if (!stale.isEmpty()) {
            logger.info("Pruned {} stale agents from AgentDAG (vertex count now {})", stale.size(), graph.vertexSet().size());
        }
    }
}
