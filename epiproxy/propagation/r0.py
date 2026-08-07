from .dag import AgentDAG

class PropagationEngine:
    def __init__(self, dag: AgentDAG):
        self.dag = dag
        self.trust_weights = {
            0: 0.0, # Human, never accepts injection
            1: 0.2, # Orchestrator, low probability
            2: 0.5, # Sub-agent, medium probability
            3: 0.9  # External tool, high probability of being trusted blindly
        }

    def compute_r0(self, source: str, target: str, classifier_score: float, trust_tier: int) -> float:
        self.dag.add_message(source, target)
        
        downstream_nodes = self.dag.get_downstream(target)
        
        p_accept = classifier_score * self.trust_weights.get(trust_tier, 0.5)
        
        # If there are no downstream nodes yet, assume at least 1 potential downstream hop in worst case 
        expected_spread = max(1, len(downstream_nodes)) * p_accept
        
        return expected_spread
