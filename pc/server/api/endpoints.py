"""
REST API endpoints.
"""
import os
import logging
from typing import List, Optional
from datetime import datetime
from fastapi import FastAPI, HTTPException, Depends, Query
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel
import json

from ..models.models import Database, Event, Device, PydanticEvent, PydanticDevice, DeviceConfig

logger = logging.getLogger(__name__)


class RegisterRequest(BaseModel):
    deviceId: str
    deviceType: str = "doorbell"
    deviceName: str = "Smart Doorbell"
    capabilities: List[str] = []
    endpoints: dict = {}


class EventCreate(BaseModel):
    id: str
    deviceId: str
    timestamp: Optional[int] = None
    type: str
    confidence: Optional[float] = None
    thumbnails: List[str] = []
    duration: Optional[int] = None


class ConfigUpdate(BaseModel):
    sensitivity: Optional[float] = None
    minDuration: Optional[int] = None
    cooldown: Optional[int] = None
    recordDuration: Optional[int] = None
    retentionDays: Optional[int] = None
    screenTimeout: Optional[int] = None


class EventResponse(BaseModel):
    id: str
    device_id: str
    timestamp: str
    event_type: str
    confidence: Optional[float]
    thumbnails: List[str]
    video_path: Optional[str]
    duration: Optional[int]
    is_read: bool


class DeviceResponse(BaseModel):
    id: str
    name: str
    device_type: str
    ip_address: Optional[str]
    port: Optional[int]
    capabilities: List[str]
    is_online: bool
    last_seen: str


def create_api(db: Database, storage_path: str) -> FastAPI:
    app = FastAPI(title="Smart Doorbell API")

    os.makedirs(storage_path, exist_ok=True)
    os.makedirs(os.path.join(storage_path, "thumbnails"), exist_ok=True)
    os.makedirs(os.path.join(storage_path, "videos"), exist_ok=True)

    @app.post("/api/devices/register", response_model=DeviceResponse)
    async def register_device(req: RegisterRequest):
        device = await db.get_device(req.deviceId)
        
        if device:
            await db.update_device(
                req.deviceId,
                name=req.deviceName,
                ip_address=req.endpoints.get("web", ""),
                is_online=True,
                last_seen=datetime.utcnow()
            )
        else:
            device = Device(
                id=req.deviceId,
                name=req.deviceName,
                device_type=req.deviceType,
                capabilities=json.dumps(req.capabilities),
                is_online=True,
                last_seen=datetime.utcnow()
            )
            await db.create_device(device)

        return DeviceResponse(
            id=device.id,
            name=device.name,
            device_type=device.device_type,
            ip_address=device.ip_address,
            port=device.port,
            capabilities=json.loads(device.capabilities) if device.capabilities else [],
            is_online=device.is_online,
            last_seen=device.last_seen.isoformat()
        )

    @app.get("/api/devices", response_model=List[DeviceResponse])
    async def list_devices():
        devices = await db.get_all_devices()
        return [
            DeviceResponse(
                id=d.id,
                name=d.name,
                device_type=d.device_type,
                ip_address=d.ip_address,
                port=d.port,
                capabilities=json.loads(d.capabilities) if d.capabilities else [],
                is_online=d.is_online,
                last_seen=d.last_seen.isoformat()
            )
            for d in devices
        ]

    @app.get("/api/devices/{device_id}", response_model=DeviceResponse)
    async def get_device(device_id: str):
        device = await db.get_device(device_id)
        if not device:
            raise HTTPException(status_code=404, detail="Device not found")
        
        return DeviceResponse(
            id=device.id,
            name=device.name,
            device_type=device.device_type,
            ip_address=device.ip_address,
            port=device.port,
            capabilities=json.loads(device.capabilities) if device.capabilities else [],
            is_online=device.is_online,
            last_seen=device.last_seen.isoformat()
        )

    @app.get("/api/events", response_model=List[EventResponse])
    async def list_events(
        device_id: Optional[str] = Query(None),
        limit: int = Query(100, ge=1, le=1000),
        offset: int = Query(0, ge=0)
    ):
        events = await db.get_events(device_id=device_id, limit=limit, offset=offset)
        return [
            EventResponse(
                id=e.id,
                device_id=e.device_id,
                timestamp=e.timestamp.isoformat(),
                event_type=e.event_type,
                confidence=e.confidence,
                thumbnails=json.loads(e.thumbnails) if e.thumbnails else [],
                video_path=e.video_path,
                duration=e.duration,
                is_read=e.is_read
            )
            for e in events
        ]

    @app.post("/api/events")
    async def create_event(event: EventCreate):
        db_event = Event(
            id=event.id,
            device_id=event.deviceId,
            timestamp=datetime.fromtimestamp(event.timestamp / 1000) if event.timestamp else datetime.utcnow(),
            event_type=event.type,
            confidence=event.confidence,
            thumbnails=json.dumps(event.thumbnails),
            duration=event.duration
        )
        await db.create_event(db_event)
        
        for thumb_url in event.thumbnails:
            await download_thumbnail(thumb_url, event.id)
        
        return {"status": "ok"}

    @app.get("/api/events/{event_id}/read")
    async def mark_event_read(event_id: str):
        await db.mark_event_read(event_id)
        return {"status": "ok"}

    @app.get("/api/config/{device_id}")
    async def get_config(device_id: str):
        config = await db.get_device_config(device_id)
        if not config:
            config = DeviceConfig()
        return config

    @app.put("/api/config/{device_id}")
    async def update_config(device_id: str, config: ConfigUpdate):
        new_config = DeviceConfig(
            sensitivity=config.sensitivity or 0.7,
            min_duration=config.minDuration or 2,
            cooldown=config.cooldown or 60,
            record_duration=config.recordDuration or 10,
            retention_days=config.retentionDays or 30,
            screen_timeout=config.screenTimeout or 30
        )
        await db.save_device_config(device_id, new_config)
        return new_config

    @app.get("/api/videos/{event_id}")
    async def get_video(event_id: str, device_address: str = Query(...)):
        video_path = os.path.join(storage_path, "videos", f"{event_id}.mp4")
        
        if os.path.exists(video_path):
            return FileResponse(video_path, media_type="video/mp4")
        
        raise HTTPException(status_code=404, detail="Video not found")

    @app.get("/api/thumbnails/{event_id}/{index}")
    async def get_thumbnail(event_id: str, index: int):
        thumb_path = os.path.join(storage_path, "thumbnails", event_id, f"{index}.jpg")
        
        if os.path.exists(thumb_path):
            return FileResponse(thumb_path, media_type="image/jpeg")
        
        raise HTTPException(status_code=404, detail="Thumbnail not found")

    @app.get("/")
    async def root():
        return {"service": "Smart Doorbell Server", "version": "1.0.0"}

    async def download_thumbnail(url: str, event_id: str):
        try:
            import aiohttp
            
            thumb_dir = os.path.join(storage_path, "thumbnails", event_id)
            os.makedirs(thumb_dir, exist_ok=True)
            
            filename = url.split("/")[-1]
            save_path = os.path.join(thumb_dir, filename)
            
            async with aiohttp.ClientSession() as session:
                async with session.get(url) as resp:
                    if resp.status == 200:
                        with open(save_path, 'wb') as f:
                            async for chunk in resp.content.iter_chunked(8192):
                                f.write(chunk)
                        logger.info(f"Downloaded thumbnail: {save_path}")
        except Exception as e:
            logger.error(f"Failed to download thumbnail: {e}")

    return app
