import json
import logging
from typing import Any, Set

logger = logging.getLogger(__name__)


class AgentWSHandler:
    def __init__(self):
        self.connections: Set[Any] = set()

    def connect(self, websocket: Any):
        self.connections.add(websocket)
        logger.info(f"Agent connected ({len(self.connections)} total)")

    def disconnect(self, websocket: Any):
        self.connections.discard(websocket)
        logger.info(f"Agent disconnected ({len(self.connections)} remaining)")

    async def broadcast(self, message: dict):
        if not self.connections:
            return
        message_str = json.dumps(message)
        disconnected = set()
        for ws in self.connections.copy():
            try:
                await ws.send_text(message_str)
            except Exception:
                disconnected.add(ws)
        self.connections -= disconnected

    @property
    def is_connected(self) -> bool:
        return len(self.connections) > 0


agent_ws_handler = AgentWSHandler()
