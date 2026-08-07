import functools
from typing import Callable, Any
from langchain_core.runnables import RunnableConfig

from ..proxy.middleware import SwarmProxy
from ..proxy.models import AgentMessage

def wrap_node(node_func: Callable, proxy: SwarmProxy, source_agent: str, target_agent: str, trust_tier: int = 2) -> Callable:
    """
    Wraps a LangGraph node function with epiproxy proxy interception.
    Assumes the state contains a 'messages' key or the state itself is convertible to string for the payload.
    """
    @functools.wraps(node_func)
    async def wrapper(state: Any, **kwargs):
        config = kwargs.get('config')
        # Extract payload from state
        if isinstance(state, dict) and "messages" in state and state["messages"]:
            last_msg = state["messages"][-1]
            payload = str(last_msg.content) if hasattr(last_msg, "content") else str(last_msg)
        elif hasattr(state, "messages") and state.messages:
            last_msg = state.messages[-1]
            payload = str(last_msg.content) if hasattr(last_msg, "content") else str(last_msg)
        else:
            payload = str(state)
            
        message = AgentMessage(
            source_agent=source_agent,
            target_agent=target_agent,
            payload=payload,
            trust_tier=trust_tier
        )
        
        async with proxy.intercept(message) as result:
            # If intercepted and quarantined, proxy.intercept raises QuarantineException.
            # Otherwise, execute the actual node function.
            
            import asyncio
            import inspect
            from ..config import settings
            import logging
            
            logger = logging.getLogger(__name__)
            
            sig = inspect.signature(node_func)
            call_kwargs = kwargs.copy()
            if 'config' in sig.parameters:
                call_kwargs['config'] = config
                
            timeout = settings.langgraph_timeout_seconds
            
            if asyncio.iscoroutinefunction(node_func):
                try:
                    return await asyncio.wait_for(node_func(state, **call_kwargs), timeout=timeout)
                except asyncio.TimeoutError:
                    logger.error(f"Node function {node_func.__name__} timed out after {timeout} seconds")
                    raise QuarantineException(f"Node execution timed out (Timeout: {timeout}s)")
            else:
                return node_func(state, **call_kwargs)

    return wrapper
