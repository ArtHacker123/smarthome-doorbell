"""
Unit tests for PC Server storage manager.
"""
import pytest
import os
import tempfile
from pathlib import Path
from server.storage.manager import StorageManager


class TestStorageManager:
    @pytest.fixture
    def storage(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            yield StorageManager(tmpdir)

    def test_initialization(self, storage):
        assert storage.videos_path.exists()
        assert storage.thumbnails_path.exists()

    def test_get_video_path(self, storage):
        path = storage.get_video_path("event-123")
        assert path.name == "event-123.mp4"

    def test_get_thumbnail_path(self, storage):
        path = storage.get_thumbnail_path("event-123", 0)
        assert path.name == "0.jpg"
        assert "event-123" in str(path)

    def test_save_and_get_video(self, storage):
        video_id = "test-video-001"
        data = b"fake video data"
        
        path = storage.save_video(video_id, data)
        
        assert path.exists()
        assert path.read_bytes() == data

    def test_save_and_get_thumbnail(self, storage):
        event_id = "test-event-001"
        index = 2
        data = b"fake image data"
        
        path = storage.save_thumbnail(event_id, index, data)
        
        assert path.exists()
        assert path.read_bytes() == data

    def test_delete_event_files(self, storage):
        event_id = "test-delete-001"
        
        # Create files
        storage.save_video(event_id, b"video")
        storage.save_thumbnail(event_id, 0, b"thumb")
        
        # Delete
        storage.delete_event_files(event_id)
        
        assert storage.get_video(event_id) is None

    def test_get_storage_stats(self, storage):
        stats = storage.get_storage_stats()
        
        assert "videos_size" in stats
        assert "thumbnails_size" in stats
        assert "videos_count" in stats
        assert "thumbnails_count" in stats
