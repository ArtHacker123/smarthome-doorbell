## ADDED Requirements

### Requirement: Android uploads snapshot after person detection
When a person detection event is triggered, the Android app SHALL upload the captured snapshot image to the PC server immediately after saving it locally.

#### Scenario: Successful snapshot upload
- **WHEN** `handleScreenshots` saves a thumbnail locally
- **THEN** Android sends HTTP POST multipart request to `/api/upload/snapshot` with `eventId` and the image file
- **THEN** server stores the file under `storage/thumbnails/<eventId>/`
- **THEN** server responds with HTTP 200 and JSON body containing `{"url": "/api/storage/thumbnails/<eventId>/thumb_0.jpg"}`

#### Scenario: Snapshot upload fails
- **WHEN** HTTP upload returns non-200 status or network error occurs
- **THEN** Android logs the error and does NOT block the event processing pipeline
- **THEN** a retry MAY be attempted on the next event cycle

### Requirement: Server stores uploaded snapshots
The PC server SHALL accept multipart file uploads and persist them to disk.

#### Scenario: Snapshot received and stored
- **WHEN** server receives POST `/api/upload/snapshot` with valid `eventId` and file
- **THEN** server creates directory `storage/thumbnails/<eventId>/` if not exists
- **THEN** server writes the uploaded file as `thumb_0.jpg`
- **THEN** server returns HTTP 200 with `{"url": "..."}`

#### Scenario: Invalid upload request
- **WHEN** request is missing `eventId` or file
- **THEN** server returns HTTP 400 with error description

### Requirement: Event payload includes snapshot URL
The `DetectionEvent` reported via WebSocket SHALL include a `thumbnails` field containing the accessible URL after successful upload.

#### Scenario: Snapshot URL in event
- **WHEN** snapshot upload completes successfully
- **THEN** the next event WebSocket message includes `"thumbnails": ["/api/storage/thumbnails/<eventId>/thumb_0.jpg"]`
