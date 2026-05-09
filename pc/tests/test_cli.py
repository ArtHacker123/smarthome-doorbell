"""
Unit tests for PC Server CLI commands.
"""
import pytest
from server.cli.commands import CLI


class TestCLI:
    @pytest.fixture
    def mock_db(self):
        class MockDevice:
            def __init__(self, id, name, device_type, is_online, last_seen):
                self.id = id
                self.name = name
                self.device_type = device_type
                self.is_online = is_online
                self.last_seen = last_seen
        
        class MockEvent:
            def __init__(self, id, device_id, timestamp, event_type, confidence, is_read):
                self.id = id
                self.device_id = device_id
                self.timestamp = timestamp
                self.event_type = event_type
                self.confidence = confidence
                self.is_read = is_read
        
        class MockDB:
            def __init__(self):
                self.devices = [
                    MockDevice("d1", "Doorbell 1", "doorbell", True, None),
                    MockDevice("d2", "Doorbell 2", "doorbell", False, None),
                ]
                self.events = [
                    MockEvent("e1", "d1", None, "person_detected", 0.95, False),
                    MockEvent("e2", "d1", None, "low_battery", 0.2, True),
                ]
            
            async def get_all_devices(self):
                return self.devices
            
            async def get_events(self, device_id=None, limit=100, offset=0):
                return self.events
            
            async def get_device_config(self, device_id):
                from server.models.models import DeviceConfig
                return DeviceConfig()
        
        return MockDB()

    @pytest.mark.asyncio
    async def test_cmd_devices_list(self, mock_db):
        cli = CLI(mock_db)
        await cli.cmd_devices_list()

    @pytest.mark.asyncio
    async def test_cmd_events_list(self, mock_db):
        cli = CLI(mock_db)
        await cli.cmd_events_list()

    @pytest.mark.asyncio
    async def test_cmd_config_show(self, mock_db):
        cli = CLI(mock_db)
        await cli.cmd_config_show("d1")

    @pytest.mark.asyncio
    async def test_cmd_server_status(self, mock_db):
        cli = CLI(mock_db)
        await cli.cmd_server_status()
