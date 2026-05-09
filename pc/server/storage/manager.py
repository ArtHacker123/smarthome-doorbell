"""
Storage module for managing video and thumbnail files.
"""
import os
import shutil
import logging
from pathlib import Path
from typing import Optional
import aiohttp
import asyncio

logger = logging.getLogger(__name__)


class StorageManager:
    def __init__(self, storage_path: str = "./storage"):
        self.storage_path = Path(storage_path)
        self.videos_path = self.storage_path / "videos"
        self.thumbnails_path = self.storage_path / "thumbnails"
        
        self.videos_path.mkdir(parents=True, exist_ok=True)
        self.thumbnails_path.mkdir(parents=True, exist_ok=True)

    async def download_thumbnail(self, url: str, device_id: str, event_id: str) -> Optional[str]:
        try:
            event_thumb_dir = self.thumbnails_path / device_id / event_id
            event_thumb_dir.mkdir(parents=True, exist_ok=True)
            
            filename = url.split("/")[-1]
            local_path = event_thumb_dir / filename
            
            async with aiohttp.ClientSession() as session:
                async with session.get(url) as response:
                    if response.status == 200:
                        content = await response.read()
                        with open(local_path, "wb") as f:
                            f.write(content)
                        logger.info(f"Downloaded thumbnail: {local_path}")
                        return str(local_path)
                    else:
                        logger.error(f"Failed to download thumbnail: {response.status}")
                        return None
                        
        except Exception as e:
            logger.error(f"Error downloading thumbnail: {e}")
            return None

    async def download_video(self, device_ip: str, video_path: str, event_id: str) -> Optional[str]:
        try:
            local_path = self.videos_path / device_ip / f"{event_id}.mp4"
            local_path.parent.mkdir(parents=True, exist_ok=True)
            
            url = f"http://{device_ip}:8080{video_path}"
            
            async with aiohttp.ClientSession() as session:
                async with session.get(url) as response:
                    if response.status == 200:
                        content = await response.read()
                        with open(local_path, "wb") as f:
                            f.write(content)
                        logger.info(f"Downloaded video: {local_path}")
                        return str(local_path)
                    else:
                        logger.error(f"Failed to download video: {response.status}")
                        return None
                        
        except Exception as e:
            logger.error(f"Error downloading video: {e}")
            return None

    def get_video_path(self, device_id: str, filename: str) -> Optional[Path]:
        path = self.videos_path / device_id / filename
        if path.exists():
            return path
        return None

    def get_thumbnail_path(self, device_id: str, event_id: str, filename: str) -> Optional[Path]:
        path = self.thumbnails_path / device_id / event_id / filename
        if path.exists():
            return path
        return None

    def list_videos(self, device_id: Optional[str] = None) -> list:
        if device_id:
            path = self.videos_path / device_id
        else:
            path = self.videos_path
            
        if not path.exists():
            return []
            
        videos = []
        for file in path.rglob("*.mp4"):
            videos.append({
                "path": str(file.relative_to(self.videos_path)),
                "size": file.stat().st_size,
                "modified": file.stat().st_mtime
            })
        return sorted(videos, key=lambda x: x["modified"], reverse=True)

    def list_thumbnails(self, device_id: str, event_id: str) -> list:
        path = self.thumbnails_path / device_id / event_id
        if not path.exists():
            return []
            
        return [str(f.relative_to(self.thumbnails_path)) for f in path.glob("*.jpg")]

    def cleanup_old_files(self, days: int = 30):
        cutoff = asyncio.get_event_loop().time() - (days * 24 * 60 * 60)
        
        for path in [self.videos_path, self.thumbnails_path]:
            for file in path.rglob("*"):
                if file.is_file() and file.stat().st_mtime < cutoff:
                    file.unlink()
                    logger.info(f"Deleted old file: {file}")
