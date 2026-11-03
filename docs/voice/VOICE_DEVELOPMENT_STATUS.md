# Voice Lab 开发状态（2026-11-03）

> 状态口径：**DONE** = 本轮真实跑通并有证据；**PARTIAL** = 有真实实现但能力不完整；
> **BLOCKED** = 依赖缺失或需授权；**NOT_VERIFIED** = 未在真实条件下验证。
> 没有证据的一律不写 DONE。

## 1. 总览

| 能力 | 状态 | 证据 / 说明 |
| --- | --- | --- |
| 本地 ASR（SenseVoiceSmall，批量） | **DONE** | 真实启动 `services/local-asr`，冷 27.15s / 温 0.81s（5.55s 音频，RTF 0.145）；经 Java `/api/voice/transcribe` 端到端 1.03s 返回原文 |
| 本地 ASR 增量转写（partial） | **BLOCKED** | 模型与服务只支持整段 WAV 一次性推理；`capabilities.partialSupported=false` 如实上报 |
| 本地 TTS | **DONE** | 新增 `services/local-tts`（macOS `say` 引擎）：4.92s 音频合成 0.69s（RTF 0.14），经 Java `/api/voice/speak` 逐句返回 PCM16 |
| 本地 TTS（sherpa-onnx 高质量模型） | **BLOCKED** | `sherpa_onnx` 已装（1.12.40），模型 167MB 下载被网络中断（GitHub 502 tunnel）；服务已实现 sherpa 引擎分支，模型就位即切换 |
| 本地 VAD | **PARTIAL** | 浏览器端 RMS 能量 VAD（阈值/最短语音/静音可调）+ 播报期抬高阈值；非模型 VAD，未做误触发率验收 |
| 本地 LLM 流式 | **DONE** | Ollama `qwen3.5:9b` 原生 `/api/chat` + `think:false`：内容 TTFT 836ms、16.3 字/秒（直接探针） |
| 本地 LLM 走既有 `/api/chat/stream` | **PARTIAL** | OpenAI 兼容端点对 `qwen3.5:9b` 只返回 `reasoning`、`content` 恒空；LOCAL 聊天需使用非思考模型（如 `qwen2.5-coder:7b`）。语音专用原生客户端 `OllamaVoiceLlm` 已实现，但尚未接入聊天落库链路 |
| 运行模式 LOCAL / HYBRID / CLOUD | **DONE** | `knowflow.voice.mode` + `voice_runtime_setting` 表 + ADMIN 切换接口；LOCAL 禁止远程降级（有单测） |
| Provider 真实能力探测 | **DONE** | `/api/voice/runtime/status` 实测返回 ASR/TTS/LLM/VAD 四项：ASR/TTS 走 `/health`，LLM 走 `/api/tags` 并核对模型是否存在 |
| Provider 路由（面试语音） | **DONE（代码+单测）** | `RoutingStreamingAsrProvider` / `RoutingTtsProvider` 按模式路由；LOCAL 绝不回退云端（单测 3 例） |
| Voice Lab 应用与路由 | **DONE（代码）** | 注册 `voice-lab`（AI 分区）+ `/voice-lab`、`/chat`、`/interview`、`/settings`、`/history` |
| AI 语音对话（复用原 Chat） | **DONE（代码）** | 采集→本地转写→既有 `/api/chat/stream`→逐句合成播报→可打断；未做真实麦克风浏览器验收 |
| 实时语音面试（复用 Interview V2） | **PARTIAL** | 路由已让面试语音在 LOCAL 模式使用本地 ASR（final-only）；状态机/评分/追问完全复用；**未在真实浏览器跑完整一场** |
| 打断（barge-in） | **PARTIAL** | 前端：播报中检测到开口即停播并中止后续合成；服务端面试通道沿用原有 VAD 打断；未量化打断延迟 |
| 语音可观测性 | **PARTIAL** | 已有结构化日志（ASR/TTS/转写耗时）、`/health` 指标；无指标落库与查询接口 |
| 完全离线验证 | **NOT_VERIFIED** | 未做断网/出口拦截测试；但 LOCAL 模式的代码路径不允许远程地址（非回环地址直接判为不可用） |
| 真实麦克风浏览器验收 | **NOT_VERIFIED** | 需用户本人在场授权麦克风；本轮只验证 HTTP 契约与本地服务 |
| 前端门禁 | **DONE** | `vue-tsc -b --force` 0 error；`app-management/navigation/icon-registry/typography` 等 106 个测试通过 |
| 后端新增单测 | **DONE** | `PcmWavTest`(5) + `VoiceRuntimeSettingTest`(4) + `LocalBatchStreamingAsrProviderTest`(5) + `RoutingStreamingAsrProviderTest`(3) 全绿 |

## 2. 本轮新增/修改文件

### 后端新增
```
backend/src/main/java/com/knowflow/voice/
  VoiceMode.java                      模式与数据出境声明
  VoiceRuntimeProperties.java         knowflow.voice.*
  VoiceRuntimeSetting.java            模式→Provider 组合
  VoiceRuntimeSettingsService.java    单行表持久化 + 进程内缓存
  VoiceCapabilityService.java         真实能力探测聚合
  VoiceRuntimeController.java         GET status / PUT mode(ADMIN)
  VoiceSpeechController.java          POST /transcribe、POST /speak
  PcmWav.java                         16k 单声道 PCM16 WAV 解析/封装
  asr/LocalAsrClient.java             本机转写客户端（仅回环）
  asr/LocalBatchStreamingAsrProvider  流式接口 + 批量能力（final-only）
  asr/RoutingStreamingAsrProvider     按模式路由
  tts/LocalTtsClient.java             本机合成客户端（NDJSON）
  tts/LocalTtsProvider.java           可取消分块合成
  tts/RoutingTtsProvider.java         按模式路由
  llm/OllamaVoiceLlm.java             原生 /api/chat（think=false）
backend/src/main/resources/db/{h2,mysql}/migration/V20261103_5__voice_runtime.sql
```

### 后端修改
- `interview/speech/SpeechStreamCapabilities.java`：新增 `partialSupported`（旧四参构造保持兼容，默认 true）。
- `interview/speech/QwenStreamingAsrProvider.java` / `QwenTtsProvider.java`：显式 bean 名，供路由注入。
- `interview/voice/VoiceAccess.java`：能力响应增加 `mode/audioLeavesDevice/leavesLocal/asrPartialSupported`，按 Provider 真实能力生成 reason。
- `interview/generation/InterviewTrainerSchema.java`：注册语音设置迁移。
- `application.yml`：新增 `knowflow.voice.*` 与 `knowflow.interview.voice.*`（全部环境变量化，默认关闭）。

### 前端新增
```
frontend/src/api/voice.ts
frontend/src/composables/useVoiceLab.ts
frontend/src/views/voice/{routes.ts, VoiceLabHome.vue, VoiceLabChat.vue, VoiceLabInterview.vue, VoiceLabSettings.vue, VoiceLabHistory.vue}
```
### 前端修改
- `apps/registry.ts`（注册 `voice-lab` + `APP_NAV_META`）、`apps/icon-registry.ts`、`router/index.ts`。

### 本地服务
```
services/local-tts/{app.py, start.sh, requirements.txt}
```

## 3. 明确未做（不要误读为完成）

1. 未接入 `LiveKit`（按需求：仅视频/多人/跨设备才引入）。
2. 未实现云端 TTS 的 Voice Lab 直连（CLOUD 模式下 `/api/voice/speak` 依赖 Qwen 配置，未验证）。
3. 未做 `Voice Metrics` 落库与轨迹查询。
4. 未做 sherpa-onnx 真机音质对比（模型未下载完）。
5. 未做 11 术语识别率基准（需要真实麦克风录音与人工 ground truth）。
6. 语音聊天未写入"语音专用"消息字段（复用原 `chat_message`，只有文本）。
