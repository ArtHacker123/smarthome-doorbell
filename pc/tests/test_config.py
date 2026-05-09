"""
Unit tests for PC Server configuration.
"""
import pytest
import os
import tempfile
from pathlib import Path
from server.core.config import Config, ServerConfig


class TestServerConfig:
    def test_default_config(self):
        config = ServerConfig()
        assert config.host == "0.0.0.0"
        assert config.port == 8000
        assert config.websocket_port == 8080
        assert config.log_level == "INFO"

    def test_custom_config(self):
        config = ServerConfig(
            host="localhost",
            port=9000,
            websocket_port=9001,
            log_level="DEBUG"
        )
        assert config.host == "localhost"
        assert config.port == 9000
        assert config.websocket_port == 9001
        assert config.log_level == "DEBUG"


class TestConfigManager:
    def test_config_singleton(self):
        # Reset singleton for testing
        Config._instance = None
        
        # Use a temp directory for config
        with tempfile.TemporaryDirectory() as tmpdir:
            config_path = Path(tmpdir) / ".doorbell"
            config_path.mkdir()
            
            config = Config()
            assert config.server.host == "0.0.0.0"
            assert config.server.port == 8000

    def test_config_update(self):
        # Reset singleton for testing
        Config._instance = None
        
        config = Config()
        config.update(host="192.168.1.100", port=8888)
        
        assert config.server.host == "192.168.1.100"
        assert config.server.port == 8888


def get_config():
    """Helper to get config instance for testing."""
    Config._instance = None
    return Config()
