from .dag import AgentDAG

class PropagationEngine:
    def __init__(self, dag: AgentDAG):
        self.dag = dag
        self.trust_weights = {
            0: 0.0, 
            1: 0.2,
            2: 0.5,
            3: 0.9  
        }

    def compute_r0(self, source: str, target: str, classifier_score: float, trust_tier: int) -> float:
        self.dag.add_message(source, target)
        
        downstream_nodes = self.dag.get_downstream(target)
        
        p_accept = classifier_score * self.trust_weights.get(trust_tier, 0.5)
        
        expected_spread = max(1, len(downstream_nodes)) * p_accept
        
        return expected_spread
