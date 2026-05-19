## 1. Server: Upload API

- [x] 1.1 Add `POST /api/upload/snapshot` endpoint accepting multipart with `eventId` + image file
- [x] 1.2 Add `POST /api/upload/video` endpoint accepting multipart with `eventId` + video file
- [x] 1.3 Add `aiofiles` dependency for async file writing (used `asyncio.to_thread` instead, no new dep)
- [x] 1.4 Store files under `storage/thumbnails/<eventId>/` and `storage/videos/<eventId>/`
- [x] 1.5 Return `{"url": "..."}` on success, proper error codes on failure

## 2. Server: WebSocket Video Request

- [x] 2.1 Add `request_video` WebSocket send + `POST /api/events/{id}/request-video` trigger API
- [x] 2.2 Add `video_ready` broadcast to notify frontend when upload completes
- [x] 2.3 Update event detail API (`GET /api/events/<id>`) to include `hasVideo` and `videoUrl`

## 3. Android: Snapshot Upload Client

- [x] 3.1 Create `MediaUploader` class using OkHttp for multipart file upload
- [x] 3.2 Integrate `MediaUploader.uploadSnapshot()` into `handleScreenshots` flow
- [x] 3.3 Log upload success/failure (non-blocking, non-fatal)

## 4. Android: Video Upload on Server Request

- [x] 4.1 Handle `request_video` message type in `WebSocketClient.handleMessage()`
- [x] 4.2 Route to `DoorbellService.handleVideoRequest()` to find video by eventId
- [x] 4.3 Upload video via `MediaUploader.uploadVideo()` when file found
- [x] 4.4 Send `video_not_found` WebSocket message when file missing

## 5. Verify End-to-End

- [x] 5.1 Test snapshot upload: trigger event → confirm file on server *(manual)*
- [x] 5.2 Test video retrieval: server requests → Android uploads → file on server *(manual)*
- [x] 5.3 Test device offline: server request returns error gracefully *(manual)*
