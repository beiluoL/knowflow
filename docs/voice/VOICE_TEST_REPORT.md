# Voice Lab 测试报告（2026-11-03）

## 1. 测试命令与结果

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量 | `mvn -o test` | **1370 例，1 失败，0 错误，12 跳过**（23m22s）|
| 前端全量 | `npx vitest run --maxWorkers=1` | **253 文件 / 1867 例全部通过**（130s）|
| 前端类型 | `npx vue-tsc -b --force` | **0 error** |
| 工程规范 | `node scripts/engineering/check.mjs` | **PASSED**（零容忍 + 棘轮，无新增项）|
| 后端受影响子集 | `-Dtest=ExamAppInstallIntegrationTest,...` | **63 例通过，BUILD SUCCESS** |
| 语音契约子集 | `-Dtest=PcmWavTest,VoiceRuntimeSettingTest,LocalBatchStreamingAsrProviderTest,RoutingStreamingAsrProviderTest,InterviewVoiceSocketTest` | **26 例通过，BUILD SUCCESS** |

## 2. 唯一失败的根因与修复

```
[ERROR] ExamAppInstallIntegrationTest.backendRecognizesEveryFrontendAppKey:90
Frontend key absent/inactive in backend: voice-lab
```

- **根因**：前端 `apps/registry.ts` 新增 `voice-lab`，后端 `OfficialAppRegistry` 白名单未同步。
  该测试是对「前后端 App 注册漂移」的既有不变量，属于真实缺陷，不是环境噪声。
- **修复**：在 `OfficialAppRegistry` 增加 `voice-lab → ACTIVE`。
- **验证**：`-Dtest=ExamAppInstallIntegrationTest` 重跑通过；受影响子集 63 例通过。
- **未重跑全量**：全量耗时 23 分钟且只需增量验证该不变量，故未再跑一遍；
  这一条按"子集验证"记，不冒充"全量全绿"。

## 3. 新增测试清单（26 例）

| 测试类 | 例数 | 覆盖 |
| --- | --- | --- |
| `PcmWavTest` | 5 | 封装/解析往返、拒绝非 WAV、拒绝立体声、拒绝超长、拒绝截断、支持含 `LIST` 附加块 |
| `VoiceRuntimeSettingTest` | 4 | LOCAL 数据不出境、HYBRID 仅文本出境、CLOUD 声明音频出境、非法模式不回落 |
| `LocalBatchStreamingAsrProviderTest` | 5 | 能力如实上报 `partialSupported=false`、仅在 finish 后产出 final、multipart 含 WAV、cancel 丢弃结果、失败映射 `ASR_UNAVAILABLE`、未配置拒绝 open |
| `RoutingStreamingAsrProviderTest` | 3 | LOCAL 即使云端可用也不切换、HYBRID 仅显式允许时降级、CLOUD 优先云端 |
| 合计 | 17 | — |

> 另：`InterviewVoiceSocketTest`（9 例）在本轮改动后仍全绿，证明面试语音协议未被破坏。

## 4. 真实服务测试（非单测）

| 项 | 数值 |
| --- | --- |
| 本地 ASR 冷请求 | 27.15s（含模型加载） |
| 本地 ASR 温请求 | 0.81s / 5.55s 音频（RTF 0.145） |
| 本地 ASR 峰值 RSS | 2.97 GiB |
| `/api/voice/transcribe` 端到端 | 1.03s，返回原文 |
| 本地 TTS 合成 | 0.69s / 4.92s 音频（RTF 0.14） |
| `/api/voice/speak` | 200，NDJSON 单块 236,160 B PCM16 |
| `/api/chat/stream` + Ollama `qwen2.5-coder:7b` | 200，SSE `start/status/answer/delta` |
| Ollama `qwen3.5:9b` native think=false | 内容 TTFT 836ms，16.3 字/秒 |
| Ollama `qwen3.5:9b` OpenAI 兼容端点 | content 恒空（只有 reasoning）→ 语音链路禁用该组合 |

## 5. 未执行的测试（不要当作通过）

- 真实麦克风浏览器端到端（需要本人点击授权）。
- 实时语音面试完整一场（需要创建 V2 会话并人工作答）。
- 断网/出口拦截的离线证明。
- MySQL 方言迁移执行。
- 11 术语识别率基准（无真实录音与 ground truth）。
- 长时间稳定性（连续十轮语音、内存增长曲线）。
