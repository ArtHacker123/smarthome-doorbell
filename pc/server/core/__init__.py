"""
Core module.
"""
from .config import Config, get_config
from .rtsp_client import RTSPClient, RTSPStreamManager, rtsp_manager

__all__ = ["Config", "get_config", "RTSPClient", "RTSPStreamManager", "rtsp_manager"]
