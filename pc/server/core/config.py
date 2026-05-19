"""
Configuration management for the doorbell server.
"""
import os
import json
from pathlib import Path
from typing import Optional
from pydantic import BaseModel


class ServerConfig(BaseModel):
    host: str = "0.0.0.0"
    port: int = 8000
    websocket_port: int = 8080
    database_url: str = "sqlite+aiosqlite:///./doorbell.db"
    storage_path: str = "./storage"
    log_level: str = "INFO"
    agent_ws_endpoint: str = "/ws/agent"
    agent_mcp_enabled: bool = True


class Config:
    _instance: Optional['Config'] = None
    _config: ServerConfig

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._load_config()
        return cls._instance

    def _load_config(self):
        config_path = Path.home() / ".doorbell" / "config.json"
        
        if config_path.exists():
            with open(config_path, 'r') as f:
                data = json.load(f)
                self._config = ServerConfig(**data)
        else:
            self._config = ServerConfig()
            self.save()

    def save(self):
        config_path = Path.home() / ".doorbell"
        config_path.mkdir(parents=True, exist_ok=True)
        
        with open(config_path / "config.json", 'w') as f:
            json.dump(self._config.model_dump(), f, indent=2)

    @property
    def server(self) -> ServerConfig:
        return self._config

    def update(self, **kwargs):
        for key, value in kwargs.items():
            if hasattr(self._config, key):
                setattr(self._config, key, value)
        self.save()


def get_config() -> Config:
    return Config()
