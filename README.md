# Smart Doorbell

AI-powered smart doorbell system with Android edge device and PC server. Detects persons at your door, records events with snapshots/video, and provides an AI Agent for natural language query.

## Architecture

```
┌─────────────────────────┐          ┌─────────────────────────────────────┐
│     Android Device      │          │          PC Server (FastAPI)         │
│                         │  ┌───────┤                                     │
│  CameraX + ML Kit       │  │  WS   │  REST API  ──── Web UI (HTML/JS)    │
│  Person Detection       │──┤       │  /ws       ──── WebSocket Server    │
│  Motion Detection       │  │  HTTP  │  /ws/agent ──── Agent WS Push       │
│  Video Recording        │  └───────┤  /mcp      ──── MCP Tools (JSON-RPC) │
│  Snapshot Capture       │          │                                     │
│  WebSocket Client       │          │  SQLite    ──── Events/Devices DB    │
│  Local RTSP Server      │          │  Storage   ──── Snapshots + Videos   │
│  DataStore Settings     │          │                                     │
└─────────────────────────┘          └───────────┬─────────────────────────┘
                                                  │
                                        ┌─────────▼─────────┐
                                        │   AI Agent         │
                                        │  (opencode)        │
                                        │  WS push + MCP     │
                                        └────────────────────┘
```

**Two communication channels:**
- **WebSocket Push** (`/ws/agent`): Server pushes detection events, video-ready notifications, device status changes to Agent in real time
- **MCP Query** (`/mcp`): Agent queries historical events, fetches snapshots, checks video info via standard MCP tools

---

## Features

### Android App

| Feature | Description |
|---|---|
| Person Detection | ML Kit Object Detection, configurable sensitivity (0.3–1.0) and cooldown |
| Motion Detection | Frame-differencing, triggers screen wake and detection cycle |
| Video Recording | Buffers frames, encodes to H.264 MP4 via MediaCodec, 3–30s |
| Snapshot Capture | JPEG thumbnail auto-uploaded to server on every event |
| Foreground Service | Persistent notification, survives app close |
| Screen Wake | Acquires wake lock on motion, auto-releases after timeout |
| Battery Alert | Reports LOW_BATTERY event when < 20% |
| Settings | 12 configurable params via Compose UI, persisted with DataStore |
| Device Registration | Auto-registers with server on connect via WebSocket |
| RTSP Server | Built-in local RTSP server (port 8554) for live stream |

### PC Server

| Feature | Description |
|---|---|
| REST API | Events, devices, config CRUD; file upload/serve |
| WebSocket Server | Real-time event broadcast, device management, heartbeat |
| Web UI | SPA with events list, live view, settings tabs — real-time WS updates |
| SQLite Database | Async SQLAlchemy, devices + events tables with `since` filtering |
| File Storage | Snapshots (`storage/thumbnails/`) and videos (`storage/videos/`) |
| CLI | Rich-formatted commands: device list, event list, config show |
| MCP Tools | 3 tools for AI Agent integration (see below) |
| Event Push | Pushes events to Agent over dedicated WebSocket (`/ws/agent`) |

### MCP Tools (for AI Agent)

Three tools exposed via standard MCP protocol (Streamable HTTP + SSE):

| Tool | Params | Returns |
|---|---|---|
| `doorbell_list_events` | `since` (ms, optional), `limit` (max 100) | Events list with snapshot/video flags |
| `doorbell_get_snapshot` | `eventId`, `format` ("url" / "base64") | Snapshot URL or base64 JPEG |
| `doorbell_get_video_info` | `eventId` | Video URL, file size, MIME type |

Configure in `~/.config/opencode/opencode.jsonc`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "doorbell": {
      "type": "remote",
      "url": "http://localhost:8000/mcp",
      "enabled": true
    }
  }
}
```

---

## Quick Start

### PC Server

```bash
cd pc
pip install -r requirements.txt
python main.py
```

Server starts at `http://0.0.0.0:8000`:
- Web UI: `http://localhost:8000`
- REST API: `http://localhost:8000/api`
- MCP endpoint: `http://localhost:8000/mcp`

### Android App

1. Open `android/` in Android Studio
2. Build and deploy to device (minSdk 26)
3. App auto-connects to server at the configured address

### Configuring Server Address

Default: `192.168.1.100:8000`. Change in app Settings screen or via config file:

```kotlin
// Android DataStore keys
PC_SERVER_HOST -> "192.168.1.100"
PC_SERVER_PORT -> 8000
```

---

## PC Server Configuration

Config file: `~/.doorbell/config.json` (auto-created on first run):

```json
{
  "server": {
    "host": "0.0.0.0",
    "port": 8000,
    "database_url": "sqlite+aiosqlite:///./doorbell.db",
    "storage_path": "./storage",
    "log_level": "INFO",
    "agent_ws_endpoint": "/ws/agent",
    "agent_mcp_enabled": true
  }
}
```

### CLI Commands

```bash
python main.py devices        # List registered devices
python main.py events         # List all events
python main.py events <id>    # List events for specific device
python main.py config <id>    # Show device config
```

### Logging

Logs output to both console and `pc/server.log` (auto-created, append mode).

---

## Android App Settings

| Setting | Default | Range | Description |
|---|---|---|---|
| Sensitivity | 0.7 | 0.3–1.0 | ML Kit detection confidence threshold |
| Min Detection Duration | 2s | 1–10s | Min time person must be detected |
| Cooldown | 60s | 10–300s | Wait time between events |
| Record Duration | 3s | 1–30s | Video recording length |
| Zoom Ratio | 9.0x | 1.0–10.0 | Camera preview zoom |
| Screen Timeout | 30s | 5–300s | Screen-off delay after motion stops |
| Motion Sensitivity | 20 | 5–100 | Frame-diff pixel change threshold |
| Pixel Threshold | 0.05 | 0.01–1.0 | Motion area ratio threshold |

---

## Event Data Flow

```
1. Detection triggered (person or motion)
       │
2. Snapshot captured ── HTTP POST ──► Server /api/upload/snapshot
       │
3. Event reported ── WS ──► Server ──► Web UI (real-time)
       │                              ├── Agent WS push
       │                              └── DB insert
       │
4. Video recording starts (3s), encodes H.264 MP4
       │
5. Server receives WS event
       │
6. If Agent requests video:
       │
       Server ── WS request_video ──► Android ── HTTP upload ──► Server
```

---

## Project Structure

```
smart-doorbell/
├── android/                        # Android app (Kotlin + Jetpack Compose)
│   ├── app/src/main/java/com/doorbell/app/
│   │   ├── ui/                     # Compose screens (Preview, Settings, Events)
│   │   ├── service/                # DoorbellService (core pipeline)
│   │   ├── network/                # WebSocket, HTTP upload, RTSP server
│   │   ├── detection/              # ML Kit person detection + motion detection
│   │   ├── storage/                # Video encoder, thumbnail manager
│   │   ├── model/                  # Data classes
│   │   └── data/                   # DataStore repository
│   └── build.gradle
├── pc/                             # PC server (Python + FastAPI)
│   ├── server/
│   │   ├── main.py                 # FastAPI app, routes, server lifecycle
│   │   ├── core/config.py          # Config singleton
│   │   ├── models/models.py        # SQLAlchemy ORM + Database class
│   │   ├── api/endpoints.py        # REST API endpoints
│   │   ├── websocket/server.py     # WebSocket manager
│   │   ├── agent/
│   │   │   ├── mcp_server.py       # MCP tools (JSON-RPC, Streamable HTTP + SSE)
│   │   │   └── ws_handler.py       # Agent WS push handler
│   │   └── templates/index.html    # Web UI
│   ├── tests/                      # Pytest test suite
│   └── main.py                     # CLI entry point
├── issues/                         # Bug postmortems
└── README.md
```

---

## API Reference

### REST Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/devices` | List devices |
| POST | `/api/devices` | Register device |
| GET | `/api/devices/{id}` | Get device info |
| PUT | `/api/devices/{id}` | Update device |
| GET | `/api/events` | List events (`?device_id=`, `?since=`, `?limit=`) |
| GET | `/api/events/{id}` | Get event details |
| POST | `/api/events/{id}/request-video` | Request video from device |
| POST | `/api/upload/snapshot` | Upload snapshot (multipart) |
| POST | `/api/upload/video` | Upload video (multipart) |
| GET | `/api/storage/thumbnails/{eventId}/{file}` | Serve snapshot file |
| GET | `/api/storage/videos/{eventId}/{file}` | Serve video file |
| GET | `/api/config/{device_id}` | Get device config |
| POST | `/api/config/{device_id}` | Save device config |

### WebSocket Messages

| Type | Direction | Description |
|---|---|---|
| `register` | Device → Server | Device registration with capabilities |
| `event` | Device → Server → All | Detection event with snapshot |
| `video_ready` | Server → All | Video file available |
| `request_video` | Server → Device | Trigger video upload |
| `video_not_found` | Device → Server → All | Video not available |
| `device_status` | Server → Agent | Device online/offline |
| `config_update` | Server → Device | Push config changes |
| `heartbeat` / `heartbeat_ack` | Bidirectional | Keep-alive |

### MCP (Streamable HTTP)

| Method | Description |
|---|---|
| `POST /mcp` | JSON-RPC request, direct JSON response |

Standard MCP handshake: `initialize` → `notifications/initialized` → `tools/list` / `tools/call`

---

## Dependencies

### Android

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 1.9.20 | Language |
| Jetpack Compose | BOM 2023.10.01 | UI framework |
| CameraX | 1.3.0 | Camera pipeline |
| ML Kit Object Detection | 17.0.0 | Person detection |
| OkHttp | 4.12.0 | WebSocket + HTTP |
| Gson | 2.10.1 | JSON serialization |
| DataStore | 1.0.0 | Settings persistence |

### PC Server

| Package | Version | Purpose |
|---|---|---|
| fastapi | 0.104.1 | REST + WebSocket framework |
| uvicorn | 0.24.0 | ASGI server |
| sqlalchemy | 2.0.23 | Async ORM |
| aiosqlite | 0.19.0 | Async SQLite driver |
| pydantic | 2.5.2 | Data validation |
| python-multipart | 0.0.6 | File upload parsing |
| rich | 13.7.0 | CLI formatting |
| pytest | 7.4.3 | Testing |

---

## License

MIT
