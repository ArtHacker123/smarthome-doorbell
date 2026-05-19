## Context

当前事件上报仅传输 JSON 元数据，截图和录像文件仅保存在手机本地 (`context.filesDir/videos/`, `context.filesDir/thumbnails/`)。服务端的 `/api/storage/thumbnails/` 和 `/api/storage/videos/` 只读取服务端本地文件，无法访问手机存储。

需要建立两个方向的媒体传输通道：
- **截图** (~50-200KB)：事件触发时由 Android 主动推送（实时性要求高）
- **录像** (几 MB)：服务端按需拉取（用户查看历史事件时触发）

## Goals / Non-Goals

**Goals:**
- Android 在事件触发后立即将截图上传至服务端并持久化
- 服务端收到截图后关联到对应事件，返回可访问 URL
- 服务端可按需向 Android 请求录像文件（用户触发查看时）
- 上传/下载过程不阻塞主事件处理流程

**Non-Goals:**
- 不支持断点续传
- 不处理录像文件的实时流式传输（视频已由 handleRecording 完整录制后归档）
- 不实现 P2P 直连（始终通过服务端中转）
- 不涉及 CDN 或云存储

## Decisions

### 1. 截图传输：HTTP POST Multipart 上传

Android 已有 `OkHttpClient` 用于 WebSocket，可复用为 HTTP 客户端。

**流程：**
1. `handleScreenshots` 保存截图到本地后，发起 HTTP POST 到 `/api/upload/snapshot`
2. 请求体为 `multipart/form-data`：包含 `eventId` + 图片文件
3. 服务端将文件存入 `storage/thumbnails/<eventId>/`，返回 URL
4. Android 得到 URL 后更新事件中的 `thumbnails` 字段（可选）

**为什么不是 WebSocket 传输二进制？**
WebSocket 传输大二进制会阻塞其他消息。独立的 HTTP POST 更可靠，支持标准重试和进度。

### 2. 录像传输：WebSocket 命令触发 → Android HTTP POST 上传

由于 Android 通常在 NAT/内网后面，服务端无法直接连接 Android。采用"服务端请求，Android 推送"模式：

**流程：**
1. 用户在服务端 Web 页面点击"查看录像"
2. 服务端通过 WebSocket 向 Android 发送 `{"type":"request_video","data":{"eventId":"..."}}`
3. Android 的 `WebSocketClient.handleMessage()` 处理此命令
4. Android 从本地找到对应录像文件，通过 HTTP POST 上传到 `/api/upload/video`
5. 服务端存储文件，返回 URL，推送通知给前端

**为什么不是服务端直接从 Android 拉取？**
- Android 可能在 NAT/防火墙后，无法直接监听入站连接
- 利用已有 WebSocket 通道作为控制信令，数据通道独立走 HTTP
- WebSocket 保持长连接，延迟最低

### 3. 不引入新依赖

- Android 复用已有的 `OkHttp` + `coroutines`
- 服务端使用已有的 `FastAPI` + `asyncio` 内置 API
- 服务端文件元数据复用已有的 SQLite `events` 表

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ Android (DoorbellService)                               │
│                                                         │
│  事件触发 ─→ DetectionCommand ─→ startEventDispatcher    │
│       │                          │                      │
│       ├── handleRecording() ─────┤ 录制 3s → MP4        │
│       │                          │                      │
│       ├── handleScreenshots() ───┤ 保存截图到本地        │
│       │     └── serviceScope.launch                     │
│       │           └── MediaUploader.uploadSnapshot() ───│─→ HTTP POST /api/upload/snapshot
│       │                          │                      │
│       └── handleEventReport() ───┤ WebSocket 上报事件    │
│                                  │                      │
│  ← WebSocket request_video ──────┤                      │
│       └── handleVideoRequest() ──┤                      │
│             └── MediaUploader.uploadVideo() ────────────│─→ HTTP POST /api/upload/video
│                                                         │
└─────────────────────────────────────────────────────────┘
         │ WebSocket                          │ HTTP
         ▼                                    ▼
┌─────────────────────────────────────────────────────────┐
│ Server (FastAPI)                                         │
│                                                         │
│  POST /api/upload/snapshot ─→ storage/thumbnails/<id>/   │
│  POST /api/upload/video    ─→ storage/videos/<id>/       │
│  POST /api/events/{id}/request-video ─→ WebSocket 通知    │
│                                                         │
│  GET /api/events ─→ 返回 hasSnapshot/hasVideo/url        │
│  GET /api/storage/thumbnails/{path} ─→ 文件服务           │
│  GET /api/storage/videos/{path}      ─→ 文件服务           │
│                                                         │
│  WebSocket 消息:                                         │
│    request_video → 通知 Android 上传录像                   │
│    video_ready   → 广播到前端，刷新页面                    │
│    video_not_found → 广播到前端，提示无法获取               │
└─────────────────────────────────────────────────────────┘
```

## API 清单

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/upload/snapshot` | 截图上传 (multipart: event_id + file) |
| POST | `/api/upload/video` | 录像上传 (multipart: event_id + file) |
| POST | `/api/events/{event_id}/request-video` | 触发录像拉取 |
| GET | `/api/events` | 事件列表 (含 hasSnapshot/hasVideo/videoUrl) |
| GET | `/api/events/{event_id}` | 事件详情 (含 hasVideo/videoUrl) |
| GET | `/api/storage/thumbnails/{path}` | 截图文件服务 |
| GET | `/api/storage/videos/{path}` | 录像文件服务 |

## 事件数据流

```
1. Android 检测到人形
2. processFrame 发送 DetectionCommand → detectionChannel
3. startEventDispatcher 并行执行:
   a. handleRecording  → 收集 3s 帧 → 编码 MP4 → 存入 videoFileMap[eventId]
   b. handleScreenshots → 保存 JPEG 截图 → 异步 POST 上传截图
   c. handleEventReport  → WebSocket 上报 JSON 事件（含 eventId）
4. 服务端存储事件到 SQLite
5. 前端通过 GET /api/events 加载列表，显示截图和录像状态
6. 用户点击 "Request Video" → POST request-video → WebSocket 通知 Android
7. Android handleVideoRequest → 查 videoFileMap → POST 上传录像
8. 服务端存储录像, 更新 DB, 广播 video_ready → 前端刷新显示
```

## 实现文件

| 文件 | 角色 |
|---|---|
| `pc/server/main.py` | 服务端 REST API + WebSocket 处理 |
| `pc/server/templates/index.html` | Web 管理页面 |
| `android/.../network/MediaUploader.kt` | Android HTTP 上传客户端 (OkHttp) |
| `android/.../network/WebSocketClient.kt` | Android WebSocket 客户端 (新增 request_video 回调) |
| `android/.../service/DoorbellService.kt` | 事件处理、截图上传触发、录像请求处理 |

## Risks / Trade-offs

- [录像上传可能较慢] → 使用协程在后台上传，不阻塞事件主循环
- [录像上传中 Android 断网] → 录像文件保留在本地，下次重连后 WebSocket 可加入重试队列
- [截图上传失败] → 事件仍正常上报，截图重试在下一帧事件触发时自动重新上传（最终一致性）
- [存储膨胀] → 服务端沿用与 Android 相同的 `retentionDays` 清理策略
