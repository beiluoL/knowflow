package com.knowflow.interview.speech;

/**
 * 配置/健康就绪度，不是付费探测结果。PCM 为有符号小端单声道。
 * <p>{@code partialSupported=false} 表示该 Provider 只能整段返回最终文本，
 * 前端必须如实展示「按句转写」，不得伪装成边说边出字。
 */
public record SpeechStreamCapabilities(
        boolean available,
        String provider,
        String format,
        int sampleRate,
        boolean partialSupported
) {

    /** 兼容既有四参调用：默认为「支持增量转写」。 */
    public SpeechStreamCapabilities(boolean available, String provider, String format, int sampleRate) {
        this(available, provider, format, sampleRate, true);
    }
}
