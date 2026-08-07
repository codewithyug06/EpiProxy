import redis
import logging
from ..config import settings

logger = logging.getLogger(__name__)

class CircuitBreaker:
    def __init__(self, threshold: float = None, redis_url: str = None):
        self.threshold = threshold if threshold is not None else settings.circuit_breaker_threshold
        url = redis_url if redis_url is not None else settings.redis_url
        self.redis = redis.from_url(url)
        self._local_fallback = set()
        logger.debug(f"Initialized CircuitBreaker with threshold {self.threshold}")

    def is_quarantined(self, agent_name: str) -> bool:
        try:
            return self.redis.exists(f"quarantine:{agent_name}") > 0
        except redis.ConnectionError as e:
            logger.warning(f"Redis connection error in is_quarantined: {e}")
            return agent_name in self._local_fallback

    def quarantine(self, agent_name: str, r0: float) -> bool:
        if r0 >= self.threshold:
            logger.info(f"Quarantining agent '{agent_name}' due to R0 ({r0}) >= {self.threshold}")
            try:
                self.redis.set(f"quarantine:{agent_name}", "true")
            except redis.ConnectionError as e:
                logger.warning(f"Redis connection error in quarantine: {e}")
                self._local_fallback.add(agent_name)
            return True
        return False
