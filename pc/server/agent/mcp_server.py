import asyncio
import base64
import json
import logging
import os
import sys
import uuid
from typing import Any, Optional

from fastapi.responses import StreamingResponse, JSONResponse
from fastapi import Request

logger = logging.getLogger(__name__)


TOOL_DEFINITIONS = [
    {
        "name": "doorbell_list_events",
        "description": "List recent doorbell detection events",
        "inputSchema": {
            "type": "object",
            "properties": {
                "since": {
                    "type": "integer",
                    "description": "Only return events after this Unix timestamp (ms)"
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of events to return (default 20, max 100)"
                }
            }
        }
    },
    {
        "name": "doorbell_get_snapshot",
        "description": "Get snapshot image for an event",
        "inputSchema": {
            "type": "object",
            "properties": {
                "eventId": {"type": "string"},
                "format": {
                    "type": "string",
                    "enum": ["url", "base64"],
                    "description": "Return format: url (default) or base64"
                }
            },
            "required": ["eventId"]
        }
    },
    {
        "name": "doorbell_get_video_info",
        "description": "Get video file information for an event",
        "inputSchema": {
            "type": "object",
            "properties": {
                "eventId": {"type": "string"}
            },
            "required": ["eventId"]
        }
    }
]


class DoorbellMCPServer:
    def __init__(self, db, storage_path: str):
        self.db = db
        self.storage_path = storage_path

    async def handle_jsonrpc(self, body: dict) -> Optional[dict]:
        method = body.get("method", "")
        params = body.get("params", {}) or {}
        request_id = body.get("id")

        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "protocolVersion": params.get("protocolVersion", "2024-11-05"),
                    "capabilities": {
                        "tools": {}
                    },
                    "serverInfo": {
                        "name": "doorbell-server",
                        "version": "1.0.0"
                    }
                }
            }

        if method == "notifications/initialized":
            return None

        if method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {"tools": TOOL_DEFINITIONS}
            }

        if method == "tools/call":
            name = params.get("name", "")
            args = params.get("arguments", {}) or {}
            try:
                if name == "doorbell_list_events":
                    result = await self._list_events(**args)
                elif name == "doorbell_get_snapshot":
                    result = await self._get_snapshot(**args)
                elif name == "doorbell_get_video_info":
                    result = await self._get_video_info(**args)
                else:
                    return {
                        "jsonrpc": "2.0",
                        "id": request_id,
                        "error": {"code": -32601, "message": f"Tool not found: {name}"}
                    }
                return {
                    "jsonrpc": "2.0",
                    "id": request_id,
                    "result": result
                }
            except Exception as e:
                logger.error(f"MCP tool error: {e}")
                return {
                    "jsonrpc": "2.0",
                    "id": request_id,
                    "error": {"code": -32000, "message": str(e)}
                }

        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "error": {"code": -32601, "message": f"Method not found: {method}"}
        }

    async def _list_events(self, since: Optional[int] = None, limit: int = 20) -> dict:
        limit = min(limit, 100)
        events = await self.db.get_events(limit=limit)
        items = []
        for e in events:
            ts = int(e.timestamp.timestamp() * 1000) if e.timestamp else 0
            if since and ts < since:
                continue
            thumb_dir = os.path.join(self.storage_path, "thumbnails", e.id)
            video_dir = os.path.join(self.storage_path, "videos", e.id)
            items.append({
                "eventId": e.id,
                "deviceId": e.device_id,
                "eventType": e.event_type,
                "confidence": e.confidence,
                "timestamp": ts,
                "hasSnapshot": os.path.exists(os.path.join(thumb_dir, "thumb_0.jpg")),
                "hasVideo": bool(e.video_path) or os.path.exists(os.path.join(video_dir, "recording.mp4")),
                "videoUrl": e.video_path or ""
            })
        return {"events": items, "total": len(items)}

    async def _get_snapshot(self, eventId: str, format: str = "url") -> dict:
        thumb_path = os.path.join(self.storage_path, "thumbnails", eventId, "thumb_0.jpg")
        if not os.path.exists(thumb_path):
            return {"error": "snapshot not found"}
        url = f"/api/storage/thumbnails/{eventId}/thumb_0.jpg"
        if format == "base64":
            with open(thumb_path, "rb") as f:
                data = base64.b64encode(f.read()).decode("utf-8")
            return {"eventId": eventId, "url": url, "base64": data, "mimeType": "image/jpeg"}
        return {"eventId": eventId, "url": url}

    async def _get_video_info(self, eventId: str) -> dict:
        video_path = os.path.join(self.storage_path, "videos", eventId, "recording.mp4")
        if not os.path.exists(video_path):
            video_path_legacy = os.path.join(self.storage_path, "videos", f"{eventId}.mp4")
            if not os.path.exists(video_path_legacy):
                return {"error": "video not found"}
            video_path = video_path_legacy
        file_size = os.path.getsize(video_path)
        url = f"/api/storage/videos/{eventId}/recording.mp4"
        return {"eventId": eventId, "videoUrl": url, "fileSize": file_size, "mimeType": "video/mp4"}

    def register_sse_routes(self, fastapi_app):
        sessions: dict[str, asyncio.Queue] = {}

        @fastapi_app.get("/mcp")
        async def mcp_sse(request: Request):
            session_id = uuid.uuid4().hex
            queue: asyncio.Queue = asyncio.Queue()
            sessions[session_id] = queue

            base_url = str(request.base_url).rstrip("/")

            async def event_stream():
                try:
                    yield f"event: endpoint\ndata: {base_url}/mcp\n\n"
                    while True:
                        try:
                            msg = await asyncio.wait_for(queue.get(), timeout=60)
                            yield f"data: {json.dumps(msg)}\n\n"
                        except asyncio.TimeoutError:
                            yield ": heartbeat\n\n"
                finally:
                    sessions.pop(session_id, None)

            return StreamingResponse(event_stream(), media_type="text/event-stream")

        @fastapi_app.post("/mcp")
        async def mcp_post(request: Request):
            body = await request.json()
            result = await self.handle_jsonrpc(body)
            if result is not None:
                for q in list(sessions.values()):
                    await q.put(result)
                return JSONResponse(content=result)
            return JSONResponse(content={"ok": True})

    async def run_stdio(self):
        loop = asyncio.get_event_loop()

        while True:
            line = await loop.run_in_executor(None, sys.stdin.readline)
            if not line:
                break
            line = line.strip()
            if not line:
                continue
            try:
                body = json.loads(line)
            except json.JSONDecodeError:
                continue
            result = await self.handle_jsonrpc(body)
            if result is not None:
                output = json.dumps(result) + "\n"
                loop.run_in_executor(None, lambda: (sys.stdout.write(output), sys.stdout.flush()))
