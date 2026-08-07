import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from .models import AgentMessage, InterceptResult
from ..classifier.heuristic import HeuristicClassifier
from ..propagation.dag import AgentDAG
from ..propagation.r0 import PropagationEngine
from ..circuit_breaker.breaker import CircuitBreaker
from ..immunity.fingerprint import ImmunityRegistry
from ..config import settings

logger = logging.getLogger(__name__)

class QuarantineException(Exception):
    pass

class SwarmProxy:
    def __init__(self):
        logger.debug("Initializing SwarmProxy components...")
        self.classifier = HeuristicClassifier()
        self.dag = AgentDAG()
        self.propagation_engine = PropagationEngine(self.dag)
        self.circuit_breaker = CircuitBreaker()
        self.immunity_registry = ImmunityRegistry()

    @asynccontextmanager
    async def intercept(self, message: AgentMessage) -> AsyncGenerator[InterceptResult, None]:
        # 1. Check if source is already quarantined
        if self.circuit_breaker.is_quarantined(message.source_agent):
            logger.warning(f"Intercept blocked: Source {message.source_agent} is quarantined.")
            raise QuarantineException(f"Agent {message.source_agent} is quarantined.")

        # 2. Check if payload signature is in immunity registry
        payload_str = str(message.payload)
        payload_hash = self.immunity_registry.compute_hash(payload_str)
        if self.immunity_registry.check_immune(payload_hash):
            logger.warning(f"Intercept blocked: Payload signature {payload_hash} matches immunity registry.")
            raise QuarantineException(f"Message payload is blocked by immunity registry. Signature: {payload_hash}")

        # 3. Score payload (now non-blocking)
        logger.debug(f"Scoring payload of length {len(payload_str)}...")
        score = await self.classifier.ascore(payload_str)
        
        # 4. Compute R0
        r0 = self.propagation_engine.compute_r0(
            source=message.source_agent,
            target=message.target_agent,
            classifier_score=score,
            trust_tier=message.trust_tier
        )
        logger.debug(f"Computed R0: {r0:.4f} (Score: {score:.4f})")

        # 5. Trigger circuit breaker if needed
        quarantined = self.circuit_breaker.quarantine(message.source_agent, r0)
        
        if quarantined:
            logger.info(f"R0 threshold exceeded! Broadcasting immune signature {payload_hash}")
            # Broadcast the blocked payload to the immunity registry
            self.immunity_registry.broadcast(payload_hash)

        result = InterceptResult(
            is_safe=not quarantined,
            score=score,
            r0=r0,
            quarantine_triggered=quarantined,
            reason="R0 threshold exceeded" if quarantined else None
        )

        if quarantined:
            raise QuarantineException(f"Message blocked. R0={r0:.2f}")
        
        # Yield control to the actual message passing mechanism
        yield result
