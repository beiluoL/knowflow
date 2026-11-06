package com.knowflow.voice.enums;

import java.util.List;

/**
 * 语音运行模式决定「哪些数据会离开本机」，不是 UI 主题选项。
 * <p>LOCAL 下禁止任何远程调用；HYBRID 只允许 LLM 出网；CLOUD 才允许云端 ASR/TTS。
 * 模式由实例级设置控制，切换需 ADMIN，避免单个用户替所有会话决定数据出境。
 */
public enum VoiceMode {
    LOCAL(
        "本地",
        "ASR、LLM、TTS、VAD 全部在本机完成，音频与文本不出本机",
        List.of("音频", "转写文本", "回答文本"),
        List.of()
    ),
    HYBRID(
        "混合",
        "语音识别与合成在本机完成，仅回答生成调用远程大模型",
        List.of("音频", "转写文本"),
        List.of("回答文本与提示词发送给所配置的大模型服务商")
    ),
    CLOUD(
        "云端",
        "ASR、LLM、TTS 全部调用远程服务，音频会上传第三方",
        List.of(),
        List.of("音频", "转写文本", "回答文本")
    );

    private final String label;
    private final String summary;
    private final List<String> staysLocal;
    private final List<String> leavesLocal;

    VoiceMode(String label, String summary, List<String> staysLocal, List<String> leavesLocal) {
        this.label = label;
        this.summary = summary;
        this.staysLocal = staysLocal;
        this.leavesLocal = leavesLocal;
    }

    public String label() {
        return label;
    }

    public String summary() {
        return summary;
    }

    public List<String> staysLocal() {
        return staysLocal;
    }

    public List<String> leavesLocal() {
        return leavesLocal;
    }

    /** 解析失败时返回 null，由调用方决定是拒绝还是回退，绝不静默切到远程。 */
    public static VoiceMode parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
