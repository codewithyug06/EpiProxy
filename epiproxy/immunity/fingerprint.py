import redis
import hashlib
import logging
from ..config import settings

logger = logging.getLogger(__name__)

class ImmunityRegistry:
    def __init__(self, redis_url: str = None):
        url = redis_url if redis_url is not None else settings.redis_url
        # Use a connection pool for better performance in production
        self.redis = redis.from_url(url, decode_responses=True)
        self.channel = "immunity_broadcast"
        logger.debug(f"Initialized ImmunityRegistry with redis url: {url}")

    def compute_hash(self, payload: str) -> str:
        return hashlib.sha256(payload.encode('utf-8')).hexdigest()

    def broadcast(self, payload_signature: str):
        try:
            self.redis.publish(self.channel, payload_signature)
            self.redis.sadd("immune_signatures", payload_signature)
            logger.info(f"Broadcasted immune signature: {payload_signature}")
        except redis.ConnectionError as e:
            logger.warning(f"Redis connection error in broadcast: {e}")

    def check_immune(self, payload_signature: str) -> bool:
        try:
            is_immune = self.redis.sismember("immune_signatures", payload_signature)
            return bool(is_immune)
        except redis.ConnectionError as e:
            logger.warning(f"Redis connection error in check_immune: {e}")
            return False
