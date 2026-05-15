"""
WebSocket server for real-time event handling.
"""
import asyncio
import json
import logging
from typing import Any, Dict, Set
from datetime import datetime
from websockets.server import WebSocketServerProtocol, serve

logger = logging.getLogger(__name__)


class WebSocketManager:
    """FastAPI-compatible WebSocket connection manager."""

    def __init__(self):
        self.clients: Set[Any] = set()
        self.device_map: Dict[str, Any] = {}

    def connect(self, websocket: Any):
        self.clients.add(websocket)
        logger.info(f"WebSocket client connected ({len(self.clients)} total)")

    def disconnect(self, websocket: Any, device_id: str = "unknown"):
        self.clients.discard(websocket)
        if device_id in self.device_map and self.device_map[device_id] == websocket:
            del self.device_map[device_id]
        logger.info(f"WebSocket client disconnected ({len(self.clients)} remaining)")

    async def handle_register(self, websocket: Any, data: dict, device_id: str = "unknown"):
        logger.info(f"Device registered: {data}")
        self.device_map[device_id] = websocket
        if hasattr(self, '_config_callback') and self._config_callback:
            config = await self._config_callback(device_id)
            if config:
                await websocket.send_json({
                    "type": "config_update",
                    "data": config
                })

    def set_config_callback(self, callback):
        self._config_callback = callback

    async def broadcast(self, message: dict):
        if not self.clients:
            return
        message_str = json.dumps(message)
        disconnected = set()
        for client in self.clients.copy():
            try:
                await client.send_text(message_str)
            except Exception:
                disconnected.add(client)
        self.clients -= disconnected


class WebSocketServer:
    def __init__(self, port: int = 8080):
        self.port = port
        self.clients: Set[WebSocketServerProtocol] = set()
        self.event_callback = None
        self.config_callback = None
        self._server = None

    def set_event_callback(self, callback):
        self.event_callback = callback

    def set_config_callback(self, callback):
        self.config_callback = callback

    async def handler(self, websocket: WebSocketServerProtocol, path: str):
        self.clients.add(websocket)
        logger.info(f"Client connected: {websocket.remote_address}")

        try:
            async for message in websocket:
                await self.process_message(websocket, message)
        except Exception as e:
            logger.error(f"WebSocket error: {e}")
        finally:
            self.clients.remove(websocket)
            logger.info(f"Client disconnected: {websocket.remote_address}")

    async def process_message(self, websocket: WebSocketServerProtocol, message: str):
        try:
            data = json.loads(message)
            msg_type = data.get("type")

            if msg_type == "register":
                await self.handle_register(websocket, data.get("data", {}))
            elif msg_type == "event":
                await self.handle_event(websocket, data.get("data", {}))
            elif msg_type == "pong":
                pass
            else:
                logger.warning(f"Unknown message type: {msg_type}")

        except json.JSONDecodeError:
            logger.error("Invalid JSON received")
        except Exception as e:
            logger.error(f"Error processing message: {e}")

    async def handle_register(self, websocket: WebSocketServerProtocol, data: dict):
        logger.info(f"Device registered: {data}")
        if self.config_callback:
            config = await self.config_callback(data.get("deviceId"))
            if config:
                await websocket.send(json.dumps({
                    "type": "config_update",
                    "data": config
                }))

    async def handle_event(self, websocket: WebSocketServerProtocol, data: dict):
        logger.info(f"Event received: {data}")
        if self.event_callback:
            await self.event_callback(data)

    async def broadcast(self, message: dict):
        if self.clients:
            message_str = json.dumps(message)
            await asyncio.gather(
                *[client.send(message_str) for client in self.clients],
                return_exceptions=True
            )

    async def start(self):
        self._server = await serve(self.handler, "0.0.0.0", self.port)
        logger.info(f"WebSocket server started on port {self.port}")

    async def stop(self):
        if self._server:
            self._server.close()
            await self._server.wait_closed()
            logger.info("WebSocket server stopped")


ws_manager = WebSocketManager()
