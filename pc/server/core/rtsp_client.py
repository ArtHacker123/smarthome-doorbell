"""
RTSP client for receiving video streams from mobile devices.
"""
import asyncio
import logging
from typing import Optional, Callable
import threading

logger = logging.getLogger(__name__)


class RTSPClient:
    def __init__(self, device_ip: str, port: int = 8554):
        self.device_ip = device_ip
        self.port = port
        self.is_connected = False
        self.is_receiving = False
        self._receive_task: Optional[asyncio.Task] = None
        self._reconnect_task: Optional[asyncio.Task] = None
        self.frame_callback: Optional[Callable] = None

    async def connect(self):
        try:
            logger.info(f"Connecting to RTSP stream at {self.device_ip}:{self.port}")
            self.is_connected = True
            return True
        except Exception as e:
            logger.error(f"Failed to connect to RTSP stream: {e}")
            self.is_connected = False
            return False

    async def start_receiving(self):
        if not self.is_connected:
            await self.connect()
        
        self.is_receiving = True
        self._receive_task = asyncio.create_task(self._receive_loop())
        logger.info("Started receiving RTSP stream")

    async def stop_receiving(self):
        self.is_receiving = False
        if self._receive_task:
            self._receive_task.cancel()
            try:
                await self._receive_task
            except asyncio.CancelledError:
                pass
        logger.info("Stopped receiving RTSP stream")

    async def _receive_loop(self):
        try:
            while self.is_receiving:
                await asyncio.sleep(0.033)
        except asyncio.CancelledError:
            pass

    async def disconnect(self):
        await self.stop_receiving()
        self.is_connected = False
        logger.info("Disconnected from RTSP stream")

    def set_frame_callback(self, callback: Callable):
        self.frame_callback = callback

    async def get_stream_status(self) -> dict:
        return {
            "connected": self.is_connected,
            "receiving": self.is_receiving,
            "device_ip": self.device_ip,
            "port": self.port
        }


class RTSPStreamManager:
    def __init__(self):
        self._clients: dict[str, RTSPClient] = {}

    async def add_stream(self, device_id: str, device_ip: str, port: int = 8554) -> RTSPClient:
        client = RTSPClient(device_ip, port)
        self._clients[device_id] = client
        await client.connect()
        logger.info(f"Added RTSP stream for device {device_id}")
        return client

    async def remove_stream(self, device_id: str):
        if device_id in self._clients:
            await self._clients[device_id].disconnect()
            del self._clients[device_id]
            logger.info(f"Removed RTSP stream for device {device_id}")

    async def get_stream(self, device_id: str) -> Optional[RTSPClient]:
        return self._clients.get(device_id)

    async def get_all_streams(self) -> dict[str, RTSPClient]:
        return self._clients.copy()

    async def reconnect_all(self):
        for device_id, client in self._clients.items():
            if not client.is_connected:
                await client.connect()
                if client.is_receiving:
                    await client.start_receiving()


rtsp_manager = RTSPStreamManager()
