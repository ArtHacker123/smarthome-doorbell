"""
Unit tests for PC Server RTSP client.
"""
import pytest
from server.core.rtsp_client import RTSPClient, RTSPStreamManager


class TestRTSPClient:
    @pytest.fixture
    def client(self):
        return RTSPClient("192.168.1.100", 8554)

    def test_client_initialization(self, client):
        assert client.device_ip == "192.168.1.100"
        assert client.port == 8554
        assert client.is_connected == False
        assert client.is_receiving == False

    @pytest.mark.asyncio
    async def test_connect(self, client):
        result = await client.connect()
        assert result == True
        assert client.is_connected == True

    @pytest.mark.asyncio
    async def test_start_and_stop_receiving(self, client):
        await client.connect()
        
        await client.start_receiving()
        assert client.is_receiving == True
        
        await client.stop_receiving()
        assert client.is_receiving == False

    @pytest.mark.asyncio
    async def test_disconnect(self, client):
        await client.connect()
        await client.start_receiving()
        
        await client.disconnect()
        
        assert client.is_connected == False
        assert client.is_receiving == False

    @pytest.mark.asyncio
    async def test_get_stream_status(self, client):
        await client.connect()
        
        status = await client.get_stream_status()
        
        assert status["connected"] == True
        assert status["receiving"] == False
        assert status["device_ip"] == "192.168.1.100"
        assert status["port"] == 8554


class TestRTSPStreamManager:
    @pytest.fixture
    def manager(self):
        return RTSPStreamManager()

    @pytest.mark.asyncio
    async def test_add_stream(self, manager):
        client = await manager.add_stream("device-001", "192.168.1.100")
        
        assert client is not None
        assert client.device_ip == "192.168.1.100"
        assert "device-001" in manager._clients

    @pytest.mark.asyncio
    async def test_remove_stream(self, manager):
        await manager.add_stream("device-001", "192.168.1.100")
        await manager.remove_stream("device-001")
        
        assert "device-001" not in manager._clients

    @pytest.mark.asyncio
    async def test_get_stream(self, manager):
        await manager.add_stream("device-001", "192.168.1.100")
        
        client = await manager.get_stream("device-001")
        assert client is not None
        assert client.device_ip == "192.168.1.100"

    @pytest.mark.asyncio
    async def test_get_all_streams(self, manager):
        await manager.add_stream("device-001", "192.168.1.100")
        await manager.add_stream("device-002", "192.168.1.101")
        
        streams = await manager.get_all_streams()
        assert len(streams) == 2
