package com.knowflow.voice.asr;

import com.knowflow.interview.speech.InterviewStreamingAsrProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import com.knowflow.voice.VoiceMode;
import com.knowflow.voice.VoiceRuntimeSettingsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 按实例运行模式路由 ASR。LOCAL 绝不回退到云端；
 * 只有 HYBRID/CLOUD 且显式开启远程降级时才允许换路，避免「悄悄上传音频」。
 */
@Component
@Primary
public class RoutingStreamingAsrProvider implements InterviewStreamingAsrProvider {

    private final InterviewStreamingAsrProvider local;
    private final InterviewStreamingAsrProvider cloud;
    private final VoiceRuntimeSettingsService settings;

    public RoutingStreamingAsrProvider(
            @Qualifier("localBatchStreamingAsrProvider") InterviewStreamingAsrProvider local,
            @Qualifier("qwenStreamingAsrProvider") InterviewStreamingAsrProvider cloud,
            VoiceRuntimeSettingsService settings) {
        this.local = local;
        this.cloud = cloud;
        this.settings = settings;
    }

    private InterviewStreamingAsrProvider route() {
        var setting = settings.current();
        boolean cloudPreferred = setting.mode() == VoiceMode.CLOUD;
        InterviewStreamingAsrProvider preferred = cloudPreferred ? cloud : local;
        InterviewStreamingAsrProvider alternate = cloudPreferred ? local : cloud;
        if (preferred.capabilities().available()) return preferred;
        if (setting.mode() != VoiceMode.LOCAL && setting.allowRemoteFallback()
                && alternate.capabilities().available()) {
            return alternate;
        }
        return preferred;
    }

    @Override
    public SpeechStreamCapabilities capabilities() {
        return route().capabilities();
    }

    @Override
    public Stream open(String requestId, Listener listener) {
        return route().open(requestId, listener);
    }
}
