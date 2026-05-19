## Why

当前事件上报只传输了 JSON 元数据，截图和录像文件只保存在手机本地，服务端无法获取媒体文件。需要建立文件传输机制，让服务端能访问到事件对应的截图和录像。

## What Changes

- 事件触发时，Android 将截图照片通过 HTTP POST 主动推送到服务端
- 服务端按需从 Android 拉取录像文件（用户查看历史事件时触发下载）
- 服务端新增 REST API：截图上传、录像请求/传输
- 事件上报 payload 中附带截图和录像的访问链接

## Capabilities

### New Capabilities
- `snapshot-upload`: Android 实时推送截图到服务端并存储
- `video-retrieval`: 服务端按需拉取 Android 上的录像文件

### Modified Capabilities
- *(none)*

## Impact

- **Android**: 新增 HTTP 上传客户端 (OkHttp)，事件流程中增加上传步骤
- **PC Server**: 新增截图上传 API、录像拉取 API、媒体文件存储管理
- 无新增第三方依赖
