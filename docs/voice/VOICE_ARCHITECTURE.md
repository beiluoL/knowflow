# Voice Runtime 架构

## 1. 分层

```
浏览器
  ├─ useInterviewPcmCapture    采集（AudioWorklet，20ms/640B PCM16 16kHz，AEC/NS/AGC）
  ├─ useVoiceLab               能量 VAD 断句 + 编排 + 打断
  └─ PcmPlayback               PCM16 队列播放（按 sequence 严格排序，可整代中断）

HTTP（全部需登录）
  ├─ POST /api/voice/transcribe   16k 单声道 PCM16 WAV → 文本
  ├─ POST /api/voice/speak        文本 → NDJSON(PCM16 分块)
  ├─ GET  /api/voice/runtime/status   真实能力探测
  ├─ PUT  /api/voice/runtime/mode     切换实例模式（ADMIN）
  └─ POST /api/chat/stream        既有聊天（会话/RAG/Tutor 全部复用）

WebSocket（面试通道，原样保留）
  └─ /ws/interview/voice/{sessionId} → InterviewVoiceSocket → InterviewV2Service
```

## 2. 后端结构

```
com.knowflow.voice
├── VoiceMode / VoiceRuntimeSetting / VoiceRuntimeSettingsService   模式与数据出境
├── VoiceCapabilityService                                         Provider 真实状态
├── VoiceSpeechController                                          听 / 说 两个原子能力
├── PcmWav                                                         唯一支持的 WAV 格式
├── asr/
│   ├── LocalAsrClient          本机 SenseVoice（仅 127.0.0.1）
│   ├── LocalBatchStreamingAsrProvider   实现流式接口，能力为 final-only
│   └── RoutingStreamingAsrProvider      @Primary，按模式路由
├── tts/
│   ├── LocalTtsClient          NDJSON PCM16，仅 127.0.0.1
│   ├── LocalTtsProvider        可取消分块合成
│   └── RoutingTtsProvider      @Primary，按模式路由
└── llm/
    └── OllamaVoiceLlm          原生 /api/chat + think=false（TTFT 可控）
```

**关键设计**：语音基础设施与业务解耦。

- 面试语音：`InterviewVoiceSocket` 注入的是 `InterviewStreamingAsrProvider`/`InterviewTtsProvider` 接口，
  换成路由 bean 后**业务代码零改动**，仍由 `InterviewV2Service` 决定题目、追问与评分。
- 语音聊天：**不新建聊天服务**，转写文本直接交给既有 `/api/chat/stream`，会话与消息落在原表。

## 3. 模式与 Provider 组合

| 模式 | ASR | LLM | TTS | VAD | 数据出境 |
| --- | --- | --- | --- | --- | --- |
| LOCAL | LOCAL_SENSEVOICE | OLLAMA | LOCAL_SAY / LOCAL_SHERPA | CLIENT_ENERGY | 无 |
| HYBRID | LOCAL_SENSEVOICE | AI_SERVICE（远程） | 本地 | CLIENT_ENERGY | 回答文本与提示词 |
| CLOUD | QWEN_REALTIME_ASR | AI_SERVICE | QWEN_REALTIME_TTS | QWEN_SERVER_VAD | 音频、转写、回答 |

路由规则（有单测）：

1. 选主 Provider；可用则直接用。
2. 主 Provider 不可用时，**只有** `mode != LOCAL` 且 `allowRemoteFallback=true` 才切备用。
3. LOCAL 模式即使云端可用也不切换——避免"本地模式偷偷上传音频"。

## 4. 一次语音回合（Voice Lab 聊天）

```
按下麦克风
  → 采集 20ms 帧；RMS ≥ 阈值累计 ≥240ms 判定开口
  → 静音 ≥700ms 判定说完
  → 拼 44 字节 WAV 头上传 /api/voice/transcribe
  → 本机 SenseVoice 返回 final 文本（约 1s，含 5.5s 音频）
  → POST /api/chat/stream（同一会话，RAG 可选）
  → 按句边界切分增量文本 → 逐句 POST /api/voice/speak
  → PcmPlayback 按 sequence 排队播放
  → 播报中检测到开口：停播 + abort 合成 + 停止本轮后续朗读
```

## 5. 为什么本地 ASR 不是"流式"

`sherpa-onnx` / SenseVoice 的批量模型只有在整段音频结束后才能给出稳定结果；
本地服务 `app.py` 明确拒绝 `Transfer-Encoding: chunked`。因此：

- 接口层沿用 `InterviewStreamingAsrProvider`（便于统一前端协议）；
- 能力层 `partialSupported=false`；
- 前端在面试与 Voice Lab 都显示"按句转写，停止后返回文本，需校对"。

这比伪造 partial（把上一句重复当增量）更可靠，也符合"不得伪装成真正流式"的约束。
