package com.knowflow.voice.tts;

import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import com.knowflow.voice.VoiceMode;
import com.knowflow.voice.VoiceRuntimeSettingsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 按实例运行模式路由 TTS；LOCAL 不回退云端，避免回答文本被悄悄外发。 */
@Component
@Primary
public class RoutingTtsProvider implements InterviewTtsProvider {

    private final InterviewTtsProvider local;
    private final InterviewTtsProvider cloud;
    private final VoiceRuntimeSettingsService settings;

    public RoutingTtsProvider(
            @Qualifier("localTtsProvider") InterviewTtsProvider local,
            @Qualifier("qwenTtsProvider") InterviewTtsProvider cloud,
            VoiceRuntimeSettingsService settings) {
        this.local = local;
        this.cloud = cloud;
        this.settings = settings;
    }

    private InterviewTtsProvider route() {
        var setting = settings.current();
        boolean cloudPreferred = setting.mode() == VoiceMode.CLOUD;
        InterviewTtsProvider preferred = cloudPreferred ? cloud : local;
        InterviewTtsProvider alternate = cloudPreferred ? local : cloud;
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
    public Stream synthesizeStream(String text, String requestId, Listener listener) {
        return route().synthesizeStream(text, requestId, listener);
    }
}
