# Voice Lab 验收报告（2026-11-03）

> 原则：只有真实执行过的才写"通过"；需要本人在场或外部依赖的，写"未验证"并说明原因。

## 1. 本轮验收环境

| 项 | 值 |
| --- | --- |
| 机型/系统 | macOS darwin（arm64），磁盘剩余约 7.1GiB |
| JDK / Maven | corretto-17.0.14 / maven 3.8.3（离线模式 `-o`） |
| 后端端口 | `50078`（`SERVER__PORT`），H2 内存库 |
| 本地 ASR | `127.0.0.1:8008`，SenseVoiceSmall（893MiB，hash 已核验） |
| 本地 TTS | `127.0.0.1:8010`，引擎 `say`（macOS 系统语音，24kHz） |
| LLM | Ollama `127.0.0.1:11434`，`qwen3.5:9b` / `qwen2.5-coder:7b` |
| 运行模式 | `LOCAL` |

## 2. 已验证（有真实响应/日志）

### 2.1 本地 ASR 服务

| 检查 | 结果 |
| --- | --- |
| 官方人声样本 5.5467s | HTTP 200，原文「欢迎大家来体验达摩院推出的语音识别模型。」 |
| 冷请求 | 27.15s（含首次模型加载与磁盘读取） |
| 温请求 | 0.81s，RTF 0.145 |
| 服务内部模型加载 | 2.629s |
| 峰值 RSS | 3,190,554,624 B（≈2.97GiB） |

### 2.2 本地 TTS 服务

| 检查 | 结果 |
| --- | --- |
| `/health` | `engine=say`，`sampleRate=24000`，`loaded=true` |
| 合成「请介绍一下 ConcurrentHashMap 的底层实现。它使用 CAS 和 synchronized。」 | 0.69s 返回 4.92s 音频（236,160 B PCM16） |
| 分句 | 文本长度 ≤60 字时合并为 1 块；更长文本按标点切句逐块下发 |

### 2.3 Java 语音接口（真实 HTTP）

| 接口 | 结果 |
| --- | --- |
| `GET /api/voice/runtime/status` | 200；LOCAL；ASR/TTS/LLM/VAD 四项均真实探测，`asr.partialSupported=false`，`tts.provider=LOCAL_SAY` |
| `POST /api/voice/transcribe`（真实 WAV 上传） | 200；`audioMs=5546`，`latencyMs=1028`，返回原文 |
| `POST /api/voice/speak` | 200；NDJSON 单块 `sampleRate=24000`、4.92s 音频、`last=true` |
| `POST /api/chat/stream`（本地 Ollama `qwen2.5-coder:7b`） | 200；SSE `start/status/answer/delta` 正常增量输出 |
| 未登录访问 `/api/voice/runtime/status` | 401（鉴权生效） |
| 非 ADMIN 调用 `PUT /api/voice/runtime/mode` | 403（未用管理员令牌验证成功路径） |

### 2.4 静态与单测

| 项 | 结果 |
| --- | --- |
| 后端编译（`mvn -o -DskipTests compile`） | 通过 |
| 新增后端单测（PcmWav / VoiceRuntimeSetting / LocalBatchStreamingAsr / Routing） | 17 例通过 |
| 前端 `npx vue-tsc -b --force` | 0 error |
| 前端注册/导航/图标/排版测试 | 9 个文件 106 例通过（新增 `voice-lab` 未破坏不变量） |

## 3. 未验证（明确列出，不当作通过）

| 项 | 原因 | 影响 |
| --- | --- | --- |
| 真实麦克风浏览器全链路 | 需要你本人在场点击授权麦克风 | 采集→VAD→转写→播报→打断的真实体感未知 |
| 实时语音面试完整一场 | 同上 + 需创建 V2 会话并人工作答 | 本地模式下 `final-only` 的等待体验未验证 |
| 打断延迟量化 | 无真实播放与真实说话 | 只有代码路径，无数字 |
| 完全离线（断网） | 未做出口拦截 | LOCAL 模式代码禁止回环外地址，但未做黑盒证明 |
| 云端 Provider（Qwen ASR/TTS） | 无云端语音 Key | CLOUD 模式仅配置级实现 |
| MySQL | 本轮仅 H2 | 迁移脚本 H2/MySQL 双份但未在 MySQL 执行 |
| sherpa-onnx 音质 | 模型下载被网络阻断（GitHub 502） | 当前用系统语音，音色与自然度有限 |
| 11 术语识别率 | 无真实录音与人工 ground truth | 不能给出准确率数字 |

## 4. 复现命令

```sh
# 1) 本地 ASR / TTS（见 VOICE_LOCAL_SETUP.md 的环境变量）
sh services/local-asr/start.sh
sh services/local-tts/start.sh

# 2) 后端
SERVER__PORT=50078 KNOWFLOW_VOICE_MODE=LOCAL \
KNOWFLOW_LOCAL_ASR_ENABLED=true KNOWFLOW_LOCAL_ASR_TOKEN=... \
KNOWFLOW_LOCAL_TTS_ENABLED=true KNOWFLOW_LOCAL_TTS_TOKEN=... \
KNOWFLOW_OLLAMA_ENABLED=true \
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--knowflow.datasource.config-file=/tmp/kf-voice-ds.json"

# 3) 验收
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:50078/api/voice/runtime/status
curl -s -H "Authorization: Bearer $TOKEN" -F "file=@sample.wav;type=audio/wav" \
  http://127.0.0.1:50078/api/voice/transcribe
```
