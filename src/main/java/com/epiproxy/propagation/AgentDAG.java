package com.epiproxy.propagation;

import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.Graphs;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AgentDAG {
    private final DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph;
    private final ReentrantReadWriteLock lock;

    public AgentDAG() {
        this.graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        this.lock = new ReentrantReadWriteLock();
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

            DefaultWeightedEdge edge = graph.getEdge(source, target);
            if (edge != null) {
                double weight = graph.getEdgeWeight(edge);
                graph.setEdgeWeight(edge, weight + 1.0);
            } else {
                Graphs.addEdge(graph, source, target, 1.0);
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
}
