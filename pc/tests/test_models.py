"""
Unit tests for PC Server models.
"""
import pytest
from datetime import datetime
from server.models.models import (
    Device, Event, DeviceConfig, Database
)


class TestDeviceConfig:
    def test_default_config(self):
        config = DeviceConfig()
        assert config.sensitivity == 0.7
        assert config.min_duration == 2
        assert config.cooldown == 60
        assert config.record_duration == 10
        assert config.retention_days == 30
        assert config.screen_timeout == 30

    def test_custom_config(self):
        config = DeviceConfig(
            sensitivity=0.9,
            min_duration=5,
            cooldown=120,
            record_duration=15,
            retention_days=7,
            screen_timeout=60
        )
        assert config.sensitivity == 0.9
        assert config.min_duration == 5
        assert config.cooldown == 120
        assert config.record_duration == 15
        assert config.retention_days == 7
        assert config.screen_timeout == 60


class TestDevice:
    def test_device_creation(self):
        device = Device(
            id="test-device-001",
            name="Test Doorbell",
            device_type="doorbell",
            capabilities='["video_stream", "event_detection"]',
            is_online=True
        )
        
        assert device.id == "test-device-001"
        assert device.name == "Test Doorbell"
        assert device.device_type == "doorbell"
        assert device.is_online == True


class TestEvent:
    def test_event_creation(self):
        event = Event(
            id="event-001",
            device_id="device-001",
            timestamp=datetime.now(),
            event_type="person_detected",
            confidence=0.95,
            duration=10
        )
        
        assert event.id == "event-001"
        assert event.device_id == "device-001"
        assert event.event_type == "person_detected"
        assert event.confidence == 0.95
        assert event.duration == 10
        assert event.is_read == False

    def test_event_types(self):
        event_types = [
            "person_detected",
            "low_battery",
            "storage_low",
            "device_offline"
        ]
        
        for event_type in event_types:
            event = Event(
                id=f"event-{event_type}",
                device_id="device-001",
                timestamp=datetime.now(),
                event_type=event_type
            )
            assert event.event_type == event_type


class TestDatabase:
    @pytest.mark.asyncio
    async def test_database_init(self):
        db = Database("sqlite+aiosqlite:///:memory:")
        await db.init_db()
        
    @pytest.mark.asyncio
    async def test_create_and_get_device(self):
        db = Database("sqlite+aiosqlite:///:memory:")
        await db.init_db()
        
        device = Device(
            id="test-device",
            name="Test",
            device_type="doorbell"
        )
        
        created = await db.create_device(device)
        assert created.id == "test-device"
        
        retrieved = await db.get_device("test-device")
        assert retrieved is not None
        assert retrieved.id == "test-device"
        assert retrieved.name == "Test"

    @pytest.mark.asyncio
    async def test_get_all_devices(self):
        db = Database("sqlite+aiosqlite:///:memory:")
        await db.init_db()
        
        await db.create_device(Device(id="d1", name="Device 1", device_type="doorbell"))
        await db.create_device(Device(id="d2", name="Device 2", device_type="doorbell"))
        
        devices = await db.get_all_devices()
        assert len(devices) == 2

    @pytest.mark.asyncio
    async def test_create_and_get_event(self):
        db = Database("sqlite+aiosqlite:///:memory:")
        await db.init_db()
        
        event = Event(
            id="event-001",
            device_id="device-001",
            timestamp=datetime.now(),
            event_type="person_detected",
            confidence=0.95,
            duration=10
        )
        
        await db.create_event(event)
        
        events = await db.get_events(limit=10)
        assert len(events) == 1
        assert events[0].id == "event-001"

    @pytest.mark.asyncio
    async def test_mark_event_read(self):
        db = Database("sqlite+aiosqlite:///:memory:")
        await db.init_db()
        
        event = Event(
            id="event-001",
            device_id="device-001",
            timestamp=datetime.now(),
            event_type="person_detected"
        )
        await db.create_event(event)
        
        assert event.is_read == False
        await db.mark_event_read("event-001")
        
        events = await db.get_events(limit=10)
        assert events[0].is_read == True
