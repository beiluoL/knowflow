# Voice API 协议

所有 HTTP 接口都需要登录（`Authorization: Bearer <jwt>`），并遵循项目统一返回体 `{code,message,data}`。

## 1. 运行时状态

`GET /api/voice/runtime/status`

```json
{
  "code": 200,
  "data": {
    "mode": {
      "value": "LOCAL", "label": "本地",
      "summary": "ASR、LLM、TTS、VAD 全部在本机完成，音频与文本不出本机",
      "staysLocal": ["音频", "转写文本", "回答文本"],
      "leavesLocal": [],
      "audioLeavesDevice": false,
      "allowRemoteFallback": false,
      "updatedAt": null, "updatedBy": null
    },
    "providers": [
      { "kind": "ASR", "provider": "LOCAL_SENSEVOICE", "available": true, "remote": false,
        "partialSupported": false, "detail": "按句转写：停止录音后返回完整文本" },
      { "kind": "TTS", "provider": "LOCAL_SAY", "available": true, "remote": false,
        "partialSupported": true, "detail": "模型已加载（引擎 say）" },
      { "kind": "LLM", "provider": "OLLAMA", "available": true, "remote": false,
        "partialSupported": true, "detail": "模型已就绪" },
      { "kind": "VAD", "provider": "CLIENT_ENERGY", "available": true, "remote": false,
        "partialSupported": false, "detail": "浏览器端能量 VAD，本机判定" }
    ],
    "interviewVoice": { "enabled": true, "partialSupported": false,
      "reason": "本地模式为按句转写，需校对后提交" },
    "generatedAt": "2026-11-03T07:03:41Z"
  }
}
```

**语义保证**：`available` 反映真实探测——本机服务走 `/health`，Ollama 走 `/api/tags` 并核对模型名；
云端 Provider 只反映配置就绪，并明确标注未做付费探测。

## 2. 切换运行模式（ADMIN）

`PUT /api/voice/runtime/mode`

```json
{ "mode": "LOCAL", "allowRemoteFallback": false }
```

- 非 ADMIN → `403`；非法模式 → `400`；LOCAL + 允许远程降级 → `400`（不允许自相矛盾配置）。
- 影响整机所有语音会话，因此不是个人偏好。

## 3. 转写

`POST /api/voice/transcribe`（`multipart/form-data`）

| 字段 | 约束 |
| --- | --- |
| `file` | WAV，16kHz、单声道、PCM16，≤60s，≤5MB |

```json
{"code":200,"data":{"text":"欢迎大家来体验达摩院推出的语音识别模型。","provider":"LOCAL_SENSEVOICE","audioMs":5546,"latencyMs":1028,"at":"..."}}
```

- 只接受上述唯一格式（按 RIFF 块遍历，允许 `LIST/fact` 等附加块）；
- 不返回置信度、不做术语替换、不猜测空音频内容；
- 本地服务未启动 → `503` 且文案提示改用文字。

## 4. 合成

`POST /api/voice/speak`

```json
{ "text": "请介绍一下 ConcurrentHashMap 的底层实现。", "speed": 1.0 }
```

响应 `application/x-ndjson`，每行一个分块：

```json
{"sequence":0,"sampleRate":24000,"audio":"<base64 PCM16>","last":false}
{"sequence":1,"sampleRate":24000,"audio":"<base64 PCM16>","last":true}
```

- 按句合成、逐句下发；`last=true` 表示本段结束；
- 客户端 abort 请求即停止后续合成（服务端检测到写失败后释放）；
- 文本 ≤2000 字；服务忙 → `429`；未启用 → `503`。

## 5. 聊天（复用既有协议，不新增）

`POST /api/chat/stream`（SSE）事件：`start` / `status` / `rag_start` / `rag_step` / `rag_done` /
`answer` / `delta` / `message` / `done` / `error`。

语音对话只是把转写文本交给这个端点，因此 **会话、消息落库、RAG、Tutor 行为与打字完全一致**。

## 6. 面试语音（既有 WebSocket，仅新增字段）

`GET /api/interview/v2/sessions/{id}/voice/capabilities` 新增：

| 字段 | 含义 |
| --- | --- |
| `mode` / `modeLabel` | 当前运行模式 |
| `audioLeavesDevice` | 音频是否离开本机 |
| `leavesLocal` | 出境数据清单 |
| `asrPartialSupported` | 是否支持增量转写（本地模式为 `false`） |

`/ws/interview/voice/{sessionId}` 协议、事件与序号规则**未改动**；
`session.ready` 的 `capabilities` 自动携带上述字段。
LOCAL 模式下 `asr.partial` 不会出现，客户端应在 `audio.stop` 后等待 `asr.final`。
