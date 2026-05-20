# MCP tools/call 返回格式不兼容导致 Agent 查询为空问题总结

## 问题现象

通过 opencode 调用 MCP `doorbell_list_events` 工具查询今天的事件时，Agent 回复"没有找到今天的事件"或返回为空。服务端日志确认查询正确、数据已返回，但 opencode 未展示结果。

## 根因分析

### MCP 协议要求

根据 MCP 规范，`tools/call` 的响应 `result` 中必须包含 `content` 数组：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{ ... serialized data ... }"
      }
    ]
  }
}
```

### 实际返回格式

服务端返回的响应是裸数据，缺少 `content` 包装：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "events": [ ... ],
    "total": 4
  }
}
```

### 涉及的工具

| 工具 | 错误的返回格式 |
|---|---|
| `doorbell_list_events` | `{"events": [...], "total": N}` |
| `doorbell_get_snapshot` | `{"eventId": "...", "url": "..."}` / `{"error": "..."}` |
| `doorbell_get_video_info` | `{"eventId": "...", "videoUrl": "...", ...}` / `{"error": "..."}` |

三个工具均不符合 MCP 协议，opencode 收到响应后因找不到 `result.content` 静默丢弃，不展示结果。

### 定位过程

1. 服务端新增详细日志（`pc/server/agent/mcp_server.py`、`pc/server/models/models.py`）
2. 日志同时输出到控制台和 `pc/server.log`
3. 确认：
   - Agent 发来的参数：`since=1779206400000`（今天午夜）、`limit=100`
   - 数据库查询 SQL：`WHERE events.timestamp >= '2026-05-20 00:00:00'`
   - 数据库返回 4 行（今日 4 条事件）
   - `_list_events` 构建了 4 个 items 并 `returning 4 items in result`
   - MCP POST response 完整发送了 4 条事件 JSON
4. 排除服务端数据问题后，确认是响应格式不符合 MCP 协议

## 解决方案

三个工具的 `result` 统一改为 MCP 标准 `content` 数组格式，数据序列化为 JSON 字符串放入 `text` 字段：

```python
# _list_events
return {"content": [{"type": "text", "text": json.dumps({"events": items, "total": len(items)})}]}

# _get_snapshot
return {"content": [{"type": "text", "text": json.dumps({"eventId": eventId, "url": url})}]}

# _get_video_info
return {"content": [{"type": "text", "text": json.dumps({"eventId": eventId, "videoUrl": url, ...})}]}
```

修改文件：`pc/server/agent/mcp_server.py`

## 相关日志

日志文件：`pc/server.log`（启动后自动追加）

关键日志片段：
```
_list_events called: since=1779206400000 (type=int), limit=100 (type=int)
  since=1779206400000 -> datetime=2026-05-20T00:00:00
[DB] get_events applied filter: Event.timestamp >= 2026-05-20T00:00:00
[DB] get_events returned 4 rows
  returning 4 items in result
MCP POST response: {"jsonrpc": "2.0", "id": 2, "result": {"events": [...], "total": 4}}
```
