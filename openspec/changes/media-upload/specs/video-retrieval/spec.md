## ADDED Requirements

### Requirement: Server can request video from Android
The PC server SHALL be able to request a video file from the Android device on demand, triggered when a user views an event's video.

#### Scenario: Server requests video via WebSocket
- **WHEN** a user clicks "view video" on an event in the server web UI
- **THEN** server sends WebSocket message `{"type":"request_video","data":{"eventId":"..."}}` to the Android device
- **THEN** Android receives the message within 5 seconds (device online)
- **THEN** Android initiates video upload

#### Scenario: Device offline
- **WHEN** Android device is not connected (WebSocket closed)
- **THEN** server returns error response `{"error":"device offline"}`
- **THEN** user is notified that the device is offline

### Requirement: Android uploads video on server request
When Android receives a `request_video` WebSocket command, it SHALL locate the recorded video file and upload it to the server.

#### Scenario: Video found and uploaded
- **WHEN** Android receives `request_video` with a valid `eventId`
- **THEN** Android finds the video file by matching event timestamp to local storage
- **THEN** Android sends HTTP POST multipart request to `/api/upload/video` with `eventId` and the MP4 file
- **THEN** server stores the file under `storage/videos/<eventId>/` and returns URL
- **THEN** server sends WebSocket message `{"type":"video_ready","data":{"eventId":"...","url":"..."}}` to notify frontend

#### Scenario: Video file not found locally
- **WHEN** Android cannot find the video file for the requested `eventId`
- **THEN** Android sends WebSocket message `{"type":"video_not_found","data":{"eventId":"..."}}`
- **THEN** server notifies the user that the video is unavailable

### Requirement: Server stores uploaded videos
The PC server SHALL accept video uploads and persist them with proper retention.

#### Scenario: Video received and stored
- **WHEN** server receives POST `/api/upload/video` with valid `eventId` and MP4 file
- **THEN** server creates directory `storage/videos/<eventId>/` and saves the file as `recording.mp4`
- **THEN** server updates the event record in the database with the video path
- **THEN** server returns HTTP 200 with `{"url": "/api/storage/videos/<eventId>/recording.mp4"}`

### Requirement: Video availability in event history
The event detail response SHALL indicate whether a video is available for viewing.

#### Scenario: Video available indicator
- **WHEN** client fetches event details via `GET /api/events/<event_id>`
- **THEN** response includes `"hasVideo": true` and `"videoUrl": "/api/storage/videos/.../recording.mp4"` if video has been uploaded
- **THEN** response includes `"hasVideo": false` if no video has been uploaded
