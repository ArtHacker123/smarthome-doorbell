"""
WebSocket server for real-time event handling.
"""
import asyncio
import json
import logging
from typing import Dict, Set
from datetime import datetime
from websockets.server import WebSocketServerProtocol, serve

logger = logging.getLogger(__name__)


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
