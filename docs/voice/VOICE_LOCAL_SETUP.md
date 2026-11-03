# 本地语音环境搭建与启动

> 目标：不依赖任何远程 ASR/TTS/LLM API，跑通「说话 → 识别 → 回答 → 播报」。
> 所有服务只监听 `127.0.0.1`，token 由你本机生成，不进代码、不进 git。

## 0. 前置检查

```sh
ollama list                      # 需要至少一个对话模型
lsof -nP -iTCP:11434 -sTCP:LISTEN # Ollama 必须在跑
lsof -nP -iTCP:8008 -sTCP:LISTEN  # 本地 ASR（未跑则下一步启动）
lsof -nP -iTCP:8010 -sTCP:LISTEN  # 本地 TTS
```

本机实测：`qwen3.5:9b`(6.6GB) / `qwen2.5-coder:7b`(4.7GB) / `bge-m3` 等已安装；磁盘剩余仅约 7GiB，新增模型前先确认空间。

## 1. 本地 ASR（SenseVoiceSmall，批量）

模型目录（已就位，893MiB，五项文件 hash 见 `verification.json`）：

```
~/.cache/knowflow-local-asr/SenseVoiceSmall/3847d57b6bdf2dd8875cb1508d2af43d80a16bf7
```

启动：

```sh
export KNOWFLOW_LOCAL_ASR_ENABLED=true
export KNOWFLOW_LOCAL_ASR_MODEL_DIR="$HOME/.cache/knowflow-local-asr/SenseVoiceSmall/3847d57b6bdf2dd8875cb1508d2af43d80a16bf7"
export KNOWFLOW_LOCAL_ASR_TOKEN="$(openssl rand -hex 16)"
export KNOWFLOW_LOCAL_ASR_PORT=8008
sh services/local-asr/start.sh
```

> ⚠️ 内存峰值约 **2.97 GiB**（实测），与 Ollama 同时运行需要足够内存。
> 首次请求含模型加载：实测冷请求 27.15s、温请求 0.81s（5.55s 音频）。

## 2. 本地 TTS

### 2.1 默认引擎：macOS 系统语音（零下载，离线）

```sh
export KNOWFLOW_LOCAL_TTS_ENABLED=true
export KNOWFLOW_LOCAL_TTS_TOKEN="$(openssl rand -hex 16)"
export KNOWFLOW_LOCAL_TTS_PORT=8010
export KNOWFLOW_LOCAL_TTS_ENGINE=say       # auto | say | sherpa
export KNOWFLOW_LOCAL_TTS_VOICE=Tingting   # 可用 say -v '?' 查看
sh services/local-tts/start.sh
```

实测：中文 4.92s 音频合成 **0.69s**（RTF 0.14），24kHz 单声道 PCM16。

### 2.2 可选引擎：sherpa-onnx VITS（更自然，需要模型）

```sh
/opt/homebrew/bin/python3.11 -m venv services/local-tts/.venv
services/local-tts/.venv/bin/pip install -r services/local-tts/requirements.txt
```

下载模型（约 167MB，**本机当前离线被网络阻断**，未完成）：

```sh
mkdir -p ~/.cache/knowflow-local-tts && cd ~/.cache/knowflow-local-tts
curl -L -o vits-melo-tts-zh_en.tar.bz2 \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar -xjf vits-melo-tts-zh_en.tar.bz2
export KNOWFLOW_LOCAL_TTS_MODEL_DIR="$HOME/.cache/knowflow-local-tts/vits-melo-tts-zh_en"
```

模型目录就位后，`KNOWFLOW_LOCAL_TTS_ENGINE=auto` 会自动优先使用 sherpa 引擎，
`/health` 与 `/api/voice/runtime/status` 会显示 `LOCAL_SHERPA`。

## 3. 启动后端

```sh
export SERVER__PORT=50078
export KNOWFLOW_VOICE_MODE=LOCAL
export KNOWFLOW_LOCAL_ASR_ENABLED=true
export KNOWFLOW_LOCAL_ASR_TOKEN="$ASR_TOKEN"      # 与 ASR 服务一致
export KNOWFLOW_LOCAL_TTS_ENABLED=true
export KNOWFLOW_LOCAL_TTS_TOKEN="$TTS_TOKEN"      # 与 TTS 服务一致
export KNOWFLOW_OLLAMA_ENABLED=true
export KNOWFLOW_OLLAMA_BASE_URL=http://127.0.0.1:11434
export KNOWFLOW_OLLAMA_MODEL=qwen3.5:9b
# 若要在 Voice Lab 聊天里走既有 /api/chat/stream，需要一个能返回 content 的模型：
export AI_BASE_URL=http://127.0.0.1:11434/v1
export AI_MODEL=qwen2.5-coder:7b
export AI_API_KEY=ollama-local
# 面试实时语音通道（可选）
export KNOWFLOW_INTERVIEW_VOICE_ENABLED=true
export KNOWFLOW_INTERVIEW_VOICE_ORIGINS=http://localhost:5174

mvn -o spring-boot:run -Dspring-boot.run.arguments="--knowflow.datasource.config-file=/tmp/kf-voice-ds.json"
```

> `--knowflow.datasource.config-file` 指向临时文件，避免仓库内 `config/datasource.json` 抢占数据源。

## 4. 已验证 / 未验证

| 步骤 | 状态 |
| --- | --- |
| ASR 服务启动 + 真实转写 | ✅ 实测通过 |
| TTS 服务启动 + 真实合成 | ✅ 实测通过 |
| `/api/voice/transcribe` | ✅ 实测 1.03s（5.55s 音频） |
| `/api/voice/speak` | ✅ 实测 NDJSON + PCM16 |
| `/api/voice/runtime/status` | ✅ 四项 Provider 真实状态 |
| `/api/chat/stream` + 本地 Ollama | ✅ 实测返回 answer/delta |
| 真实麦克风浏览器全链路 | ⚠️ 需你本人在场授权麦克风 |
| 断网离线验证 | ⚠️ 未做 |
