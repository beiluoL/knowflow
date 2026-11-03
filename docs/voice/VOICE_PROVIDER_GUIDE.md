# Provider 接入指南

## 1. 四个能力位

| 能力 | 接口 | 现有实现 |
| --- | --- | --- |
| 流式 ASR（面试） | `InterviewStreamingAsrProvider` | `QwenStreamingAsrProvider`（云）、`LocalBatchStreamingAsrProvider`（本地，final-only） |
| 批量 ASR（Voice Lab） | `LocalAsrClient` / `InterviewAsrProvider` | SenseVoice 本机、OpenAI 兼容远程 |
| TTS | `InterviewTtsProvider` | `QwenTtsProvider`（云）、`LocalTtsProvider`（本机） |
| 语音对话 LLM | `OllamaVoiceLlm` + 既有 `AiService` | Ollama 原生、OpenAI 兼容 |

## 2. 新增一个 Provider 的最小步骤

1. 实现对应接口，`capabilities()` **必须反映真实可用性**，不允许"配置了就当可用"。
2. 若是本机服务，地址必须校验为回环（`127.0.0.1`），否则本地模式失去意义。
3. 在对应 `Routing*Provider` 中登记路由分支（模式 → Provider）。
4. 在 `VoiceRuntimeSetting` 增加 provider 标识，并同步 `VoiceCapabilityService` 的展示文案。
5. 补单元测试：可用性、失败映射、取消语义。
6. 更新本文件与 `VOICE_DEVELOPMENT_STATUS.md`。

**禁止**：把批量能力包装成增量（伪造 `partial`）；把远程地址伪装成本地；在 Provider 内写业务状态机。

## 3. 当前实现细节

### 3.1 本地 ASR（SenseVoiceSmall）

- 服务：`services/local-asr/app.py`，`POST /v1/audio/transcriptions`，只收 16k 单声道 PCM16 WAV。
- Java：`LocalAsrClient` 把 PCM 包成 WAV（44 字节头）后 multipart 上传；响应只取 `text`。
- 适配器：`LocalBatchStreamingAsrProvider` 缓冲 PCM（≤60s），`finish()` 时异步转写，
  只回调 `finalText` + `completed`；`cancel()` 丢弃结果。
- 能力：`partialSupported=false`，UI 必须显示"按句转写"。

**实测**：冷请求 27.15s（含加载）、温请求 0.81s / 5.55s 音频（RTF 0.145）、峰值 RSS 2.97GiB。

### 3.2 本地 TTS

- 服务：`services/local-tts/app.py`，两种引擎：
  - `say`（默认）：macOS 系统语音，离线，24kHz，实测 0.69s 合成 4.92s 音频；
  - `sherpa`：sherpa-onnx VITS，模型目录就位才可用（本机模型下载被网络阻断）。
- 协议：`POST /v1/audio/speech` → `application/x-ndjson`，每行 `{sequence,sampleRate,audio,last}`。
- Java：`LocalTtsProvider` 在专用线程池里读取 NDJSON，逐块回调；`cancel()` 通过中断读取循环生效。

**能力边界**：模型/系统语音都是整句推理，不宣称逐字流式；分块粒度是"句"。

### 3.3 本地 LLM（Ollama）

- 原生 `POST /api/chat`，显式 `think:false`。
- 实测（`qwen3.5:9b`）：内容 TTFT **836ms**、16.3 字/秒。
- 对比实测（同一模型走 OpenAI 兼容端点）：**只产出 `delta.reasoning`，`content` 恒空**——
  这是接语音最容易踩的坑，`OllamaVoiceLlm` 专门绕开。

### 3.4 云端 Qwen

沿用既有实现，未改动协议；仅加了 bean 名以便路由注入。
`capabilities()` 依旧只是配置就绪判断，不做付费探测。

## 4. 新增 sherpa TTS 模型时

1. 下载模型到 `~/.cache/knowflow-local-tts/<model>`（含 `model.onnx`、`tokens.txt`、`lexicon.txt`）。
2. `KNOWFLOW_LOCAL_TTS_ENGINE=auto` 或 `sherpa`。
3. 重启本地 TTS 服务，`/health` 应返回 `"engine":"sherpa"` 与实际 `sampleRate`。
4. 若采样率不是 24kHz，Java/前端无需改动：采样率随每个 chunk 下发。
