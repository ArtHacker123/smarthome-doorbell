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
- 服务端使用已有的 `FastAPI` + `aiofiles`（文件写入）
- 服务端文件元数据复用已有的 SQLite `events` 表，或扩展 `media` 表

## Risks / Trade-offs

- [录像上传可能较慢] → 使用协程在后台上传，不阻塞事件主循环
- [录像上传中 Android 断网] → 录像文件保留在本地，下次重连后 WebSocket 可加入重试队列
- [截图上传失败] → 事件仍正常上报，截图重试在下一帧事件触发时自动重新上传（最终一致性）
- [存储膨胀] → 服务端沿用与 Android 相同的 `retentionDays` 清理策略
