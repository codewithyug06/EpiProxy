from pydantic import BaseModel
from typing import Optional, Any

class AgentMessage(BaseModel):
    source_agent: str
    target_agent: str
    payload: str
    trust_tier: int = 2 # 0: Human, 1: Orchestrator, 2: Sub-agent, 3: External tool

class InterceptResult(BaseModel):
    is_safe: bool
    score: float
    r0: float
    quarantine_triggered: bool
    reason: Optional[str] = None
