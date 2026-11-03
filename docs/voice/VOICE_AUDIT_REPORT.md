# KnowFlow Voice Lab · 真实审计报告

- 审计时间：2026-11-03 14:24–15:05（GMT+8）
- 审计基线 commit：`065edff0`（工作区干净，0 个未提交改动）
- 审计方式：读真实源码 + 启动真实本地服务 + 真实 HTTP/流式请求测量
- 证据原则：**接口存在 ≠ 功能实现 ≠ 真实可用**。本报告每条结论标注证据等级。

## 0. 结论摘要

| 结论 | 状态 |
| --- | --- |
| 已有实时语音面试链路（前端采集→WebSocket→ASR→LLM→TTS→播放→打断） | **真实实现（DONE）**，但仅走 Qwen 云端 WebSocket，默认 `enabled=false` 全关 |
| 本地 ASR（SenseVoiceSmall） | **真实可用（DONE，批量/非流式）**：冷 27.15s，热 0.81s / 5.55s 音频（RTF 0.145） |
| 本地 LLM（Ollama） | **真实可用（DONE）**：`qwen3.5:9b` 关闭思考后内容 TTFT 836ms，16.3 字符/秒 |
| 本地 TTS | **不存在（BLOCKED）**：无 sherpa-onnx、无任何本地合成实现 |
| 本地 VAD | **不存在（GAP）**：只有 Qwen 云端 `server_vad`；前端只算 RMS 音量，不判起止 |
| Local / Hybrid / Cloud 运行模式 | **不存在（GAP）**：Provider 为单例硬绑定，无模式切换、无能力探测端点 |
| Voice Lab 应用 / AI 语音聊天 | **不存在（GAP）**：只有面试语音，聊天无语音入口 |
| Voice Metrics 聚合 | **部分（PARTIAL）**：仅 `log.info` 打点，无指标存储/查询接口 |
| 真实浏览器麦克风验收 | **NOT_VERIFIED（本轮）**：需用户授权麦克风；历史 2026-10-15 有过批量录音验收记录 |

**一句话**：项目已有一套**高质量但纯云端**的实时语音面试实现，和一套**真实可用但非流式**的本地 ASR。要做「Local-first 实时语音」，缺的是 **本地 TTS + 本地 VAD + Provider 路由/模式 + 语音聊天业务**，而不是重写面试状态机。

---

## 1. 审计方法与证据等级

| 等级 | 含义 |
| --- | --- |
| DONE（实测） | 本轮真实启动服务并跑通请求，有耗时/返回原文 |
| DONE（代码） | 源码为完整生产实现，有测试，但本轮未跑真实外部依赖 |
| PARTIAL | 有实现但有明确能力缺口（如只有批量、没有流式） |
| STUB / 不可用 | 接口存在但无真实实现，或依赖缺失 |
| NOT_VERIFIED | 无法在本轮以真实条件验证（缺授权/缺设备/缺模型） |

本轮**没有**修改任何业务代码，只读 + 起停本地服务。

---

## 2. Git 与工作区

```
$ git status --short | wc -l
0
$ git log --oneline -1
065edff0 feat: 求职工作台 V3 与工程规范体系(简历工作室/岗位流水线/多工具入口)
```

- 分支：`main`，无未跟踪文件，无暂存改动。
- 未创建新分支/worktree，未 reset，未清理任何用户文件，未 commit/push。

---

## 3. 现有实时语音链路（真实代码路径）

### 3.1 前端（Vue3）

| 文件 | 行数 | 真实能力 |
| --- | --- | --- |
| `frontend/src/composables/useInterviewPcmCapture.ts` | 170 | getUserMedia（`echoCancellation/noiseSuppression/autoGainControl` 全开）→ AudioWorklet → 20ms/640B PCM16 单声道 16kHz 帧；权限 20s 超时、`OverconstrainedError` 降级重试、帧序列校验、ack 背压、字节/时长上限、RMS 音量、卸载释放麦克风 |
| `frontend/src/interview/voice/pcm16.worklet.js` | 80 | 采集线程内重采样到 16k、产出 PCM16 帧、ack 才继续（防积压） |
| `frontend/src/interview/voice/pcmPlayback.ts` | 102 | PCM16 播放队列、按 `sequence` 排序、打断清队列、音量控制 |
| `frontend/src/composables/useInterviewRealtimeVoice.ts` | 261 | 票据→WS→`session.start`→`audio.start`→推帧→`asr.partial/final`→草稿校对→`audio.commit`→`llm.delta`→`tts.*` 播放→`control.interrupt` 打断；心跳、指数退避重连（≤6 次）、服务端版本/序列校验、`error` 收敛 |
| `frontend/src/components/interview/InterviewRealtimeVoice.vue` ｜ `InterviewV2Panel.vue` | 65 ｜ 140+ | 已挂载到 Interview V2 面板（`InterviewV2Panel.vue:140`），受 `disabled` 门控 |

**判定**：前端不是 Demo，是完整实现。**但**：无客户端 VAD（`volume` 只用于 UI），无本地 ASR/TTS 概念，`capability` 完全来自后端。

### 3.2 后端（Spring Boot）

| 文件 | 行数 | 真实能力 |
| --- | --- | --- |
| `interview/voice/InterviewVoiceSocket.java` | 1158 | 有界串行队列（64）、每用户连接配额（32B 帧上限、1292B 二进制帧）、事件去重与序号强校验、音频摘要 SHA-256、`begin/saveFinal` 幂等、版本+题目 ID 双重并发守卫、`generationCancelled` 防旧 Turn 覆盖、打断（服务端 VAD `speech_started` + 300ms 冷却） |
| `interview/voice/VoiceProtocol.java` | 114 | 事件白名单、字段/长度校验、二进制帧解析 |
| `interview/voice/VoiceTicketStore.java` | 158 | 短时票据、续期、绑定 user/session |
| `interview/voice/VoiceAccess.java` | 97 | 归属校验与 capability 输出 |
| `interview/speech/InterviewStreamingAsrProvider.java` | 22 | 流式 ASR 契约（partial/final/started/ended/completed/error） |
| `interview/speech/QwenStreamingAsrProvider.java` | 85 | **唯一**流式实现：Qwen Realtime WS + `server_vad` |
| `interview/speech/InterviewTtsProvider.java` ｜ `QwenTtsProvider.java` | 15 ｜ 64 | **唯一** TTS 实现：Qwen Realtime 24kHz PCM16 |
| `interview/speech/InterviewSpeechService.java` | 54 | 批量录音转写：归属/题目/时长/幂等/哈希、只存「待确认转写」，不写答案与评分 |
| `interview/v2/InterviewV2Service.java` | 355 | 面试状态机（含 `answerStreaming` 增量追问） |
| `interview/v2/InterviewFollowupEngine.java` | 130 | 追问生成 |

**判定**：骨架非常扎实，安全边界（票据、Origin、配额、序列、防重放）已就位。**关键结构性缺陷**：

1. `InterviewVoiceSocket` 直接注入**单一** bean：
   ```java
   private final InterviewStreamingAsrProvider asr;
   private final InterviewTtsProvider tts;
   ```
   两个接口各只有一个 `@Component` 实现（Qwen）。**没有 Provider 注册表、没有模式概念，本地 Provider 无处插入。**
2. `asr.capabilities()` 与 `tts.capabilities()` 只反映「配置是否齐」，**不代表服务真的活着**（`SpeechStreamCapabilities.java:1` 注释自认 "never a paid health probe"）。
3. 全项目 `application.yml` **没有任何** `knowflow.interview.voice.*` / `realtime-speech.*` / `speech.*` 配置 → 默认 `enabled=false`，开箱即用语音能力为 0。

---

## 4. 本地服务与环境实测

### 4.1 已装模型（`ollama list`，真实输出）

| 模型 | 体积 | 用途 |
| --- | --- | --- |
| `qwen3.5:9b` | 6.6 GB | 唯一通用对话模型（思考型） |
| `qwen2.5-coder:7b` | 4.7 GB | 代码 |
| `deepseek-r1:7b` | 4.7 GB | 推理 |
| `bge-m3` / `qwen3-embedding:0.6b` / `nomic-embed-text` | ≤1.2 GB | 向量 |

`ollama` 进程真实监听 `127.0.0.1:11434`（PID 794）。

### 4.2 本地 LLM 实测（真实流式请求）

| 模型/协议 | 内容 TTFT | 总耗时 | 吞吐 | 备注 |
| --- | --- | --- | --- | --- |
| `qwen3.5:9b` + OpenAI `/v1/chat/completions` | **内容永不出现** | 23.2s | 0 | 该模型先流 `delta.reasoning`，`content` 为空 → 直接接语音会「哑巴」 |
| `qwen3.5:9b` + 原生 `/api/chat` + `"think": false` | **836 ms** | 6.44s（105 字） | 16.3 字/秒 | **推荐**：语音链路必须关思考 |
| `qwen2.5-coder:7b` + OpenAI 端点 | 3921 ms | 6.27s（48 字） | 7.7 字/秒 | 冷加载；非对话优化 |

> 结论：**语音必须走 Ollama 原生 `/api/chat` 并显式 `think:false`**，否则首字延迟不可控甚至无内容。

### 4.3 本地 ASR 实测（真实启动 `services/local-asr/app.py`）

模型：`~/.cache/knowflow-local-asr/SenseVoiceSmall/3847d57b6bdf2dd8875cb1508d2af43d80a16bf7`（893 MiB，五项文件 hash 已核验，见同目录 `verification.json`）。

| 指标 | 实测值 |
| --- | --- |
| 冷请求（含模型加载 + 首次磁盘读） | HTTP 200，**27.15 s** |
| 温请求（5.5467s 音频） | HTTP 200，**0.812 s** |
| 服务内部转写 | 0.806 s，**RTF 0.145** |
| 模型构造耗时 | 2.629 s |
| 进程峰值 RSS | 3,190,554,624 B ≈ **2.97 GiB** |
| 转写原文 | `欢迎大家来体验达摩院推出的语音识别模型。` |

**能力边界（源码 `app.py` 明确）**：

- 只接受 `multipart` + 16kHz 单声道 PCM16 WAV，**整段一次请求**；
- 拒绝 `Transfer-Encoding: chunked`（`:110`），**没有 partial transcript**；
- 单请求 ≤120s、≤10MiB，单模型串行，忙则 429；
- `KNOWFLOW_LOCAL_ASR_ENABLED` 默认 false，必须有 token；
- 不访问数据库、不写评分。

> 结论：**它是「按句批量转写」，不是流式 ASR**。强行包装成“实时转写”属于造假，本报告不做此声明。

### 4.4 本地 TTS

- 无 `sherpa-onnx`（`importlib.util.find_spec('sherpa_onnx')` → `False`）；
- 无任何 `.onnx` TTS 模型；
- 现有 `QwenTtsProvider` 是**云** TTS，本地模式下不可用。

> **BLOCKED**：本地 TTS 从零开始，需要安装运行时 + 下载模型（约 100–200 MB）。

### 4.5 本地 VAD

- 后端无 VAD 抽象，仅 `QwenStreamingAsrProvider` 把 `server_vad` 参数透传给云端；
- 前端 `useInterviewPcmCapture.ts:129-132` 计算了 RMS，但只赋给 `volume`，**不判语音起止**；
- 无 Silero、无 webrtcvad、无 `sherpa-onnx` VAD。

### 4.6 其他环境事实

| 项 | 实测 |
| --- | --- |
| 磁盘剩余 | **7.1 GiB**（99% 已用）→ 只允许小体积模型，禁止再拉 GB 级权重 |
| 后端端口 | 未运行（50078 空闲）；沙箱注入 `SERVER__PORT=50078` |
| 本地 ASR 端口 | 8008（本轮审计启动，已实测） |
| WebSocket 路由 | `/ws/interview/voice/{sessionId}`（`VoiceSocketConfig.java:27`） |
| Vite WS 代理 | 已配置 `/ws` → `ws:true`，且 `__WS_BACKEND_PORT__` 支持直连 |

---

## 5. 缺口清单（按优先级）

| # | 缺口 | 影响 | 优先级 |
| --- | --- | --- | --- |
| G1 | 无本地 TTS | 本地模式无法「AI 说话」，语音闭环断在最后一步 | **P0** |
| G2 | 无 Provider 路由 / 运行模式 | 无法 Local/Hybrid/Cloud 切换，无法声明“数据是否离开本机” | **P0** |
| G3 | 能力探测是“看配置”而非“看进程” | UI 会显示可用但实际 503 | **P0** |
| G4 | 无本地 VAD（前端只算音量） | 无法自动断句、无法本地打断判定 | **P1** |
| G5 | 本地 ASR 无流式/partial | 只能「按句转写 + 用户校对」，不能边说边出字 | **P1（受模型限制）** |
| G6 | 无 Voice Lab 应用与 AI 语音聊天 | 只有面试能语音，日常交流不能 | **P1** |
| G7 | 无 Voice Metrics 聚合与查询 | 只有日志，无法做 TTFT/打断延迟验收 | **P2** |
| G8 | 语音默认全关，无任何 yml 配置 | 新用户开箱不可用 | **P0（文档+示例配置）** |

### 重复实现风险（明确禁止）

- **禁止**再写一套面试状态机/评分：必须复用 `InterviewV2Service`、`InterviewReportAggregator`、`InterviewFollowupEngine`。
- **禁止**再写一套聊天存储：语音聊天必须复用 `chat_conversation` / `chat_message` 与 `ChatService`。
- **禁止**把 Python 语音服务做成业务状态机：它只做 ASR/TTS 数值计算。

---

## 6. 阻塞项（需用户授权，未授权不伪造结果）

| 阻塞 | 说明 | 影响 |
| --- | --- | --- |
| B1 | 真实麦克风录音（浏览器权限弹窗需本人在场） | 「真实麦克风」验收只能标 NOT_VERIFIED 或由用户自行执行脚本 |
| B2 | 下载本地 TTS 模型（约 100–200 MB） | 已获「下载模型可以联网」授权，但磁盘仅剩 7.1 GiB，需选小模型 |
| B3 | Qwen 云端语音 Key | 云端模式无法本轮复验；不影响本地模式 |
| B4 | MySQL 生产库 | 本轮只在 H2/内存库验证，MySQL 需另跑 |

---

## 7. 本轮实施路线（按 P0 → P1）

1. **P0-1 Voice Runtime 骨架**：`com.knowflow.voice` — 模式（LOCAL/HYBRID/CLOUD）、Provider 注册表、**真实**能力探测（HTTP/进程级）、配置项与默认值。
2. **P0-2 本地 ASR 适配**：`LocalBatchAsrProvider` 实现 `InterviewStreamingAsrProvider`：缓冲 PCM → `finish()` 时调 8008 → 发 `final`；`capabilities().partialSupported=false` 如实暴露，UI 不谎称“实时”。
3. **P0-3 本地 TTS**：`services/local-tts`（sherpa-onnx，VITS 中文，PCM16 分块 + 取消）→ `LocalTtsProvider` 接入。
4. **P0-4 路由接线**：`InterviewVoiceSocket` 改为注入路由 Provider，按模式选择；`session.ready` 增加 `mode`/`partialSupported`/`providers` 字段。
5. **P1-1 Voice Lab**：注册应用 + 路由 `/voice-lab`、`/voice-lab/chat`、`/voice-lab/interview`、`/voice-lab/settings`、`/voice-lab/history`；首页显示**真实** Provider 状态。
6. **P1-2 语音聊天**：复用 ChatService 会话/消息，WebSocket 语音回合（VAD 断句 → 本地 ASR → Ollama 流式 → 本地 TTS → 可打断）。
7. **P1-3 本地 VAD**：客户端 RMS+滞回（可配置阈值/最短语音/静音），并明确其为「客户端能量 VAD」，不冒充模型 VAD。
8. **P2 Metrics**：TTFT/TPS/ASR 延迟/TTS 首音/轮次延迟/打断延迟，落库 + 查询端点。

每个阶段结束执行：针对性测试 → 相关全量测试 → `git diff` 自查 → 更新本文档与 `VOICE_DEVELOPMENT_STATUS.md`。

---

## 8. 附录：本轮原始测量记录

```
ASR /health（真实响应）
{"enabled": true, "loaded": true, "metrics": {"successes": 2, "failures": 0,
 "model_load_seconds": 2.6292286249990866, "last_transcribe_seconds": 0.8057605410012911,
 "last_audio_seconds": 5.5466875, "last_real_time_factor": 0.14526878267457669,
 "peak_rss_bytes": 3190554624}}

LLM 流式（qwen3.5:9b, native /api/chat, think=false）
{"content_ttft_ms": 836, "total_s": 6.44, "chars": 105, "cps": 16.3}

LLM 流式（qwen3.5:9b, OpenAI /v1/chat/completions）
{"chunks": 0, "chars": 0, "note": "仅 delta.reasoning，无 content"}
```
