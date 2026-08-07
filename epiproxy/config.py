from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field

class Settings(BaseSettings):
    redis_url: str = Field(default="redis://localhost:6379", description="Redis connection URL")
    ml_model_name: str = Field(default="all-MiniLM-L6-v2", description="Sentence-Transformers model name")
    heuristic_threshold: float = Field(default=0.3, description="Score threshold for heuristic classifier")
    circuit_breaker_threshold: float = Field(default=1.0, description="R0 threshold to trip circuit breaker")
    
    log_level: str = Field(default="INFO", description="Logging level (DEBUG, INFO, WARNING, ERROR)")
    
    langgraph_timeout_seconds: float = Field(default=10.0, description="Timeout for LangGraph wrapper")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_prefix="EPIPROXY_",
        extra="ignore"
    )

settings = Settings()
