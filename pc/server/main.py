"""
Main server application.
"""
import asyncio
import json
import logging
import os
from pathlib import Path

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from uvicorn import Config, Server

from .core.config import get_config
from .models.models import Database
from .websocket.server import ws_manager

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class DoorbellServer:
    def __init__(self):
        self.config = get_config().server
        self.db = Database(self.config.database_url)
        self.http_server = None
        self.app = None

    async def init(self):
        await self.db.init_db()
        logger.info("Database initialized")
        
        os.makedirs(self.config.storage_path, exist_ok=True)
        os.makedirs(os.path.join(self.config.storage_path, "thumbnails"), exist_ok=True)
        os.makedirs(os.path.join(self.config.storage_path, "videos"), exist_ok=True)

    async def handle_event(self, data: dict):
        from .models.models import Event
        from datetime import datetime
        
        timestamp = data.get('timestamp', 0)
        dt = datetime.fromtimestamp(timestamp / 1000) if timestamp else datetime.utcnow()
        
        event = Event(
            id=data.get('id', ''),
            device_id=data.get('deviceId', ''),
            timestamp=dt,
            event_type=data.get('type', 'unknown'),
            confidence=data.get('confidence'),
            thumbnails=str(data.get('thumbnails', [])),
            duration=data.get('duration')
        )
        await self.db.create_event(event)
        logger.info(f"Event stored: {event.id}")
        
        await ws_manager.broadcast({
            "type": "event",
            "data": data
        })

    async def start(self):
        await self.init()

        self.app = FastAPI(title="Smart Doorbell Server")
        
        self.app.add_middleware(
            CORSMiddleware,
            allow_origins=["*"],
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )

        @self.app.get("/")
        async def root():
            template_path = Path(__file__).parent / "templates" / "index.html"
            if template_path.exists():
                return FileResponse(str(template_path))
            return {"status": "ok", "service": "Smart Doorbell Server"}

        @self.app.get("/health")
        async def health():
            return {"status": "healthy"}

        @self.app.get("/api/devices")
        async def list_devices():
            devices = await self.db.get_all_devices()
            return {"devices": [
                {
                    "id": d.id,
                    "name": d.name,
                    "type": d.device_type,
                    "online": d.is_online,
                    "lastSeen": d.last_seen.isoformat() if d.last_seen else None
                }
                for d in devices
            ]}

        @self.app.get("/api/devices/{device_id}")
        async def get_device(device_id: str):
            device = await self.db.get_device(device_id)
            if not device:
                return {"error": "not found"}
            return {
                "id": device.id,
                "name": device.name,
                "type": device.device_type,
                "online": device.is_online
            }

        @self.app.get("/api/events")
        async def list_events(device_id: str = None, limit: int = 100, offset: int = 0):
            events = await self.db.get_events(device_id, limit, offset)
            return {"events": [
                {
                    "id": e.id,
                    "deviceId": e.device_id,
                    "timestamp": e.timestamp.isoformat() if e.timestamp else None,
                    "type": e.event_type,
                    "confidence": e.confidence,
                    "thumbnails": [],
                    "duration": e.duration,
                    "isRead": e.is_read
                }
                for e in events
            ]}

        @self.app.get("/api/events/{event_id}")
        async def get_event(event_id: str):
            events = await self.db.get_events(limit=1000)
            event = next((e for e in events if e.id == event_id), None)
            if not event:
                return {"error": "not found"}
            return {
                "id": event.id,
                "deviceId": event.device_id,
                "timestamp": event.timestamp.isoformat() if event.timestamp else None,
                "type": event.event_type,
                "confidence": event.confidence
            }

        @self.app.get("/api/storage/thumbnails/{path:path}")
        async def get_thumbnail(path: str):
            file_path = os.path.join(self.config.storage_path, "thumbnails", path)
            if os.path.exists(file_path):
                return FileResponse(file_path)
            return {"error": "not found"}

        @self.app.get("/api/storage/videos/{path:path}")
        async def get_video(path: str):
            file_path = os.path.join(self.config.storage_path, "videos", path)
            if os.path.exists(file_path):
                return FileResponse(file_path, media_type="video/mp4")
            return {"error": "not found"}

        async def ws_handler(websocket: WebSocket):
            ws_manager.connect(websocket)
            await websocket.accept()
            device_id = "unknown"
            
            try:
                while True:
                    data = await websocket.receive_text()
                    message = json.loads(data)
                    
                    if message.get("type") == "register":
                        device_id = message.get("data", {}).get("deviceId", "unknown")
                        await ws_manager.handle_register(websocket, message.get("data", {}), device_id)
                    elif message.get("type") == "event":
                        await self.handle_event(message.get("data"))
                    elif message.get("type") == "heartbeat":
                        await websocket.send_json({"type": "heartbeat_ack"})
                        
            except WebSocketDisconnect:
                ws_manager.disconnect(websocket, device_id)
            except Exception as e:
                logger.error(f"WebSocket error: {e}")

        self.app.websocket_route("/ws")(ws_handler)

        config = Config(
            app=self.app,
            host=self.config.host,
            port=self.config.port,
            log_level="info"
        )
        
        self.http_server = Server(config)
        
        logger.info(f"Starting HTTP server on {self.config.host}:{self.config.port}")
        logger.info(f"WebSocket endpoint: ws://{self.config.host}:{self.config.port}/ws")
        await self.http_server.serve()

    async def stop(self):
        if self.http_server:
            self.http_server.should_exit = True
        logger.info("Server stopped")


async def main():
    server = DoorbellServer()
    
    try:
        await server.start()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        await server.stop()


if __name__ == "__main__":
    asyncio.run(main())
