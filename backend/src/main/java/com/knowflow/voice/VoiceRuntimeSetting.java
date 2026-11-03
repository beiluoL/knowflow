package com.knowflow.voice;

import java.time.Instant;
import java.util.List;

/**
 * 实例级语音运行设置的不可变快照。Provider 由模式推导，避免出现
 * 「LOCAL 模式 + 云端 TTS」这种自相矛盾且会偷传数据的组合。
 */
public record VoiceRuntimeSetting(
        VoiceMode mode,
        boolean allowRemoteFallback,
        Long updatedBy,
        Instant updatedAt
) {

    public static VoiceRuntimeSetting defaults(VoiceMode mode) {
        return new VoiceRuntimeSetting(mode, false, null, null);
    }

    public String asrProvider() {
        return mode == VoiceMode.CLOUD ? "QWEN_REALTIME_ASR" : "LOCAL_SENSEVOICE";
    }

    public String llmProvider() {
        return mode == VoiceMode.LOCAL ? "OLLAMA" : "AI_SERVICE";
    }

    public String ttsProvider() {
        return mode == VoiceMode.CLOUD ? "QWEN_REALTIME_TTS" : "LOCAL_SHERPA";
    }

    public String vadProvider() {
        return mode == VoiceMode.CLOUD ? "QWEN_SERVER_VAD" : "CLIENT_ENERGY";
    }

    /** 真实音频是否离开本机；LOCAL/HYBRID 的 ASR 都在本地。 */
    public boolean audioLeavesDevice() {
        return mode == VoiceMode.CLOUD;
    }

    public List<String> leavesLocal() {
        return mode.leavesLocal();
    }
}
