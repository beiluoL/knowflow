package com.knowflow.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 模式决定数据出境范围与 Provider 组合，必须可读、可验证、不可静默降级。 */
class VoiceRuntimeSettingTest {

    @Test
    void localModeNeverLeavesTheMachine() {
        var setting = VoiceRuntimeSetting.defaults(VoiceMode.LOCAL);
        assertFalse(setting.audioLeavesDevice());
        assertTrue(setting.leavesLocal().isEmpty());
        assertEquals("LOCAL_SENSEVOICE", setting.asrProvider());
        assertEquals("OLLAMA", setting.llmProvider());
        assertEquals("CLIENT_ENERGY", setting.vadProvider());
    }

    @Test
    void hybridSendsOnlyTextToRemoteModel() {
        var setting = VoiceRuntimeSetting.defaults(VoiceMode.HYBRID);
        assertFalse(setting.audioLeavesDevice());
        assertEquals("LOCAL_SENSEVOICE", setting.asrProvider());
        assertEquals("AI_SERVICE", setting.llmProvider());
        assertTrue(setting.leavesLocal().stream().anyMatch((item) -> item.contains("回答文本")));
    }

    @Test
    void cloudModeDeclaresAudioEgress() {
        var setting = VoiceRuntimeSetting.defaults(VoiceMode.CLOUD);
        assertTrue(setting.audioLeavesDevice());
        assertEquals("QWEN_REALTIME_ASR", setting.asrProvider());
        assertEquals("QWEN_SERVER_VAD", setting.vadProvider());
    }

    @Test
    void parseRejectsUnknownValuesInsteadOfFallingBackToRemote() {
        assertNull(VoiceMode.parse("whatever"));
        assertNull(VoiceMode.parse(null));
        assertEquals(VoiceMode.LOCAL, VoiceMode.parse(" local "));
    }
}
