"""
Database models and management.
"""
import logging
from datetime import datetime
from typing import Optional, List
from sqlalchemy import Column, String, Integer, Float, DateTime, Text, Boolean
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
from pydantic import BaseModel

logger = logging.getLogger(__name__)
Base = declarative_base()


class Device(Base):
    __tablename__ = "devices"

    id = Column(String, primary_key=True)
    name = Column(String, nullable=False)
    device_type = Column(String, nullable=False, default="doorbell")
    ip_address = Column(String)
    port = Column(Integer)
    capabilities = Column(Text)  # JSON string
    is_online = Column(Boolean, default=False)
    last_seen = Column(DateTime, default=datetime.utcnow)
    created_at = Column(DateTime, default=datetime.utcnow)


class Event(Base):
    __tablename__ = "events"

    id = Column(String, primary_key=True)
    device_id = Column(String, nullable=False)
    timestamp = Column(DateTime, default=datetime.utcnow)
    event_type = Column(String, nullable=False)
    confidence = Column(Float)
    thumbnails = Column(Text)  # JSON string
    video_path = Column(String)
    duration = Column(Integer)
    is_read = Column(Boolean, default=False)


class DeviceConfig(BaseModel):
    sensitivity: float = 0.7
    min_duration: int = 2
    cooldown: int = 60
    record_duration: int = 10
    retention_days: int = 30
    screen_timeout: int = 30


class PydanticEvent(BaseModel):
    id: str
    device_id: str
    timestamp: datetime
    event_type: str
    confidence: Optional[float] = None
    thumbnails: List[str] = []
    video_path: Optional[str] = None
    duration: Optional[int] = None
    is_read: bool = False

    class Config:
        from_attributes = True


class PydanticDevice(BaseModel):
    id: str
    name: str
    device_type: str
    ip_address: Optional[str] = None
    port: Optional[int] = None
    capabilities: List[str] = []
    is_online: bool = False
    last_seen: datetime

    class Config:
        from_attributes = True


class Database:
    def __init__(self, database_url: str):
        self.engine = create_async_engine(database_url, echo=False)
        self.async_session = async_sessionmaker(
            self.engine, class_=AsyncSession, expire_on_commit=False
        )

    async def init_db(self):
        async with self.engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)

    async def create_device(self, device: Device) -> Device:
        async with self.async_session() as session:
            session.add(device)
            await session.commit()
            await session.refresh(device)
            return device

    async def get_device(self, device_id: str) -> Optional[Device]:
        async with self.async_session() as session:
            from sqlalchemy import select
            result = await session.execute(
                select(Device).where(Device.id == device_id)
            )
            return result.scalar_one_or_none()

    async def get_all_devices(self) -> List[Device]:
        async with self.async_session() as session:
            from sqlalchemy import select
            result = await session.execute(select(Device))
            return list(result.scalars().all())

    async def update_device(self, device_id: str, **kwargs):
        async with self.async_session() as session:
            from sqlalchemy import select, update
            await session.execute(
                update(Device).where(Device.id == device_id).values(**kwargs)
            )
            await session.commit()

    async def create_event(self, event: Event) -> Event:
        async with self.async_session() as session:
            session.add(event)
            await session.commit()
            await session.refresh(event)
            return event

    async def get_events(
        self, 
        device_id: Optional[str] = None, 
        since: Optional[int] = None,
        limit: int = 100,
        offset: int = 0
    ) -> List[Event]:
        async with self.async_session() as session:
            from sqlalchemy import select, desc
            from datetime import datetime
            query = select(Event).order_by(desc(Event.timestamp)).limit(limit).offset(offset)
            
            if device_id:
                query = query.where(Event.device_id == device_id)
            if since is not None:
                since_dt = datetime.fromtimestamp(since / 1000)
                logger.info(f"[DB] get_events applying filter: Event.timestamp >= {since_dt.isoformat()} (since={since})")
                query = query.where(Event.timestamp >= since_dt)
            
            compiled = query.compile(compile_kwargs={"literal_binds": True})
            logger.info(f"[DB] get_events SQL: {compiled}")
            
            result = await session.execute(query)
            rows = list(result.scalars().all())
            logger.info(f"[DB] get_events returned {len(rows)} rows")
            if rows:
                logger.info(f"[DB] first row: id={rows[0].id[:8]} ts={rows[0].timestamp}")
                logger.info(f"[DB] last  row: id={rows[-1].id[:8]} ts={rows[-1].timestamp}")
            return rows

    async def mark_event_read(self, event_id: str):
        async with self.async_session() as session:
            from sqlalchemy import update
            await session.execute(
                update(Event).where(Event.id == event_id).values(is_read=True)
            )
            await session.commit()

    async def get_device_config(self, device_id: str) -> Optional[DeviceConfig]:
        # For now, return default config
        # In production, this would be stored per device
        return DeviceConfig()

    async def save_device_config(self, device_id: str, config: DeviceConfig):
        # For now, config is stored in memory
        # In production, this would be persisted per device
        pass
