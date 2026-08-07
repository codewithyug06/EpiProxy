import networkx as nx
from typing import List

class AgentDAG:
    def __init__(self):
        self.graph = nx.DiGraph()

    def add_message(self, source: str, target: str):
        if not self.graph.has_node(source):
            self.graph.add_node(source)
        if not self.graph.has_node(target):
            self.graph.add_node(target)
            
        if self.graph.has_edge(source, target):
            self.graph[source][target]['weight'] += 1
        else:
            self.graph.add_edge(source, target, weight=1)

    def get_downstream(self, node: str) -> List[str]:
        if not self.graph.has_node(node):
            return []
        return list(self.graph.successors(node))
