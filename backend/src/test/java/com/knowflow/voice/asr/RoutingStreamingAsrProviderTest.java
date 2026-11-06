package com.knowflow.voice.asr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knowflow.interview.speech.InterviewStreamingAsrProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import com.knowflow.voice.enums.VoiceMode;
import com.knowflow.voice.config.VoiceRuntimeProperties;
import com.knowflow.voice.model.VoiceRuntimeSetting;
import com.knowflow.voice.service.VoiceRuntimeSettingsService;
import org.junit.jupiter.api.Test;

/** LOCAL 绝不偷偷切到云端；只有 HYBRID/CLOUD 且显式允许降级时才换路。 */
class RoutingStreamingAsrProviderTest {

    private static final InterviewStreamingAsrProvider LOCAL_DOWN = provider("LOCAL_SENSEVOICE", false);
    private static final InterviewStreamingAsrProvider LOCAL_UP = provider("LOCAL_SENSEVOICE", true);
    private static final InterviewStreamingAsrProvider CLOUD_UP = provider("QWEN_REALTIME_ASR", true);

    private static InterviewStreamingAsrProvider provider(String name, boolean available) {
        return new InterviewStreamingAsrProvider() {
            @Override
            public SpeechStreamCapabilities capabilities() {
                return new SpeechStreamCapabilities(available, name, "PCM16", 16000, !"LOCAL_SENSEVOICE".equals(name));
            }

            @Override
            public Stream open(String requestId, Listener listener) {
                throw new IllegalStateException("open:" + name);
            }
        };
    }

    private static VoiceRuntimeSettingsService settings(VoiceRuntimeSetting setting) {
        return new VoiceRuntimeSettingsService(new VoiceRuntimeProperties(), null) {
            @Override
            public VoiceRuntimeSetting current() {
                return setting;
            }
        };
    }

    private RoutingStreamingAsrProvider router(InterviewStreamingAsrProvider local,
                                               InterviewStreamingAsrProvider cloud,
                                               VoiceRuntimeSetting setting) {
        return new RoutingStreamingAsrProvider(local, cloud, settings(setting));
    }

    @Test
    void localModeStaysLocalEvenWhenCloudIsAvailable() {
        var router = router(LOCAL_DOWN, CLOUD_UP,
                new VoiceRuntimeSetting(VoiceMode.LOCAL, true, null, null));
        var capabilities = router.capabilities();
        assertEquals("LOCAL_SENSEVOICE", capabilities.provider());
        assertFalse(capabilities.available(), "LOCAL 模式不得把云端能力冒充为本地可用");
    }

    @Test
    void hybridFallsBackOnlyWhenExplicitlyAllowed() {
        var allowed = router(LOCAL_DOWN, CLOUD_UP,
                new VoiceRuntimeSetting(VoiceMode.HYBRID, true, null, null));
        assertEquals("QWEN_REALTIME_ASR", allowed.capabilities().provider());

        var denied = router(LOCAL_DOWN, CLOUD_UP,
                new VoiceRuntimeSetting(VoiceMode.HYBRID, false, null, null));
        assertEquals("LOCAL_SENSEVOICE", denied.capabilities().provider());
        assertFalse(denied.capabilities().available());
    }

    @Test
    void cloudModePrefersCloud() {
        var router = router(LOCAL_UP, CLOUD_UP,
                VoiceRuntimeSetting.defaults(VoiceMode.CLOUD));
        assertEquals("QWEN_REALTIME_ASR", router.capabilities().provider());
        assertTrue(router.capabilities().partialSupported());
    }
}
