"""
Unit tests for PC Server WebSocket server.
"""
import pytest
import asyncio
from server.websocket.server import ConnectionManager, WebSocketServer


class TestConnectionManager:
    def test_manager_initialization(self):
        manager = ConnectionManager()
        assert len(manager.active_connections) == 0
        assert len(manager.device_connections) == 0

    @pytest.mark.asyncio
    async def test_connect(self):
        manager = ConnectionManager()
        
        class MockWebSocket:
            def __init__(self):
                self.accepted = False
                
            async def accept(self):
                self.accepted = True
        
        ws = MockWebSocket()
        await manager.connect(ws, "device-001")
        
        assert ws.accepted == True
        assert len(manager.active_connections) == 1
        assert "device-001" in manager.device_connections

    def test_disconnect(self):
        manager = ConnectionManager()
        
        class MockWebSocket:
            def __init__(self):
                self.id = id(self)
        
        ws = MockWebSocket()
        ws_id = ws.id
        
        # Manually add to connections
        manager.active_connections[ws_id] = ws
        manager.device_connections["device-001"] = {ws}
        
        manager.disconnect(ws, "device-001")
        
        assert len(manager.active_connections) == 0
        assert len(manager.device_connections) == 0


class TestWebSocketServer:
    def test_server_initialization(self):
        manager = ConnectionManager()
        server = WebSocketServer(manager, None)
        
        assert server.manager is manager

    @pytest.mark.asyncio
    async def test_handle_register(self):
        manager = ConnectionManager()
        
        class MockWebSocket:
            def __init__(self):
                self.messages = []
                
            async def send_json(self, msg):
                self.messages.append(msg)
        
        ws = MockWebSocket()
        server = WebSocketServer(manager, None)
        
        await server.handle_register(ws, {"deviceId": "test-001"}, "test-001")
        
        assert len(ws.messages) == 1
        assert ws.messages[0]["type"] == "registered"
