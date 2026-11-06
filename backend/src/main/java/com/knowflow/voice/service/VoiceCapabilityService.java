package com.knowflow.voice.service;

import com.knowflow.interview.speech.InterviewStreamingAsrProvider;
import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.interview.voice.VoiceTransportProperties;
import com.knowflow.ai.service.AiService;
import com.knowflow.voice.llm.OllamaVoiceLlm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.knowflow.voice.VoiceMode;
import com.knowflow.voice.VoiceRuntimeSetting;
/**
 * 语音运行时能力快照。每一项都区分「配置就绪」与「真实可用」：
 * 本机服务用 /health 探测，云端只声明配置就绪并明确标注未做付费探测。
 */
@Service
public class VoiceCapabilityService {

    private final VoiceRuntimeSettingsService settings;
    private final InterviewStreamingAsrProvider asr;
    private final InterviewTtsProvider tts;
    private final OllamaVoiceLlm ollama;
    private final AiService ai;
    private final VoiceTransportProperties transport;

    public VoiceCapabilityService(
            VoiceRuntimeSettingsService settings,
            @Qualifier("routingStreamingAsrProvider") InterviewStreamingAsrProvider asr,
            @Qualifier("routingTtsProvider") InterviewTtsProvider tts,
            OllamaVoiceLlm ollama,
            AiService ai,
            VoiceTransportProperties transport) {
        this.settings = settings;
        this.asr = asr;
        this.tts = tts;
        this.ollama = ollama;
        this.ai = ai;
        this.transport = transport;
    }

    public Map<String, Object> snapshot(Long userId) {
        VoiceRuntimeSetting setting = settings.current();
        List<Map<String, Object>> providers = new ArrayList<>();
        var asrCaps = asr.capabilities();
        providers.add(provider("ASR", asrCaps.provider(), asrCaps.available(),
                setting.mode() == VoiceMode.CLOUD, asrCaps.partialSupported(),
                asrCaps.available()
                        ? (asrCaps.partialSupported() ? "可实时转写" : "按句转写：停止录音后返回完整文本")
                        : "本机识别服务未运行或未配置"));
        var ttsCaps = tts.capabilities();
        providers.add(provider("TTS", ttsCaps.provider(), ttsCaps.available(),
                setting.mode() == VoiceMode.CLOUD, true,
                ttsCaps.available() ? "可语音播报" : "本机合成服务未运行或未配置"));

        if (setting.mode() == VoiceMode.LOCAL || setting.mode() == VoiceMode.HYBRID) {
            var status = ollama.status();
            providers.add(provider("LLM", setting.llmProvider(),
                    status.ok() && status.modelPresent(), false, true,
                    status.detail() + (status.modelPresent() ? "" : "；当前模型 " + ollama.model())));
        } else {
            providers.add(provider("LLM", "AI_SERVICE", ai.isConfigured(userId), true, true,
                    ai.isConfigured(userId) ? "远程模型已配置" : "远程模型未配置"));
        }

        providers.add(provider("VAD", setting.vadProvider(), true,
                setting.mode() == VoiceMode.CLOUD, false,
                setting.mode() == VoiceMode.CLOUD ? "云端服务端 VAD 断句" : "浏览器端能量 VAD，本机判定"));

        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("value", setting.mode().name());
        mode.put("label", setting.mode().label());
        mode.put("summary", setting.mode().summary());
        mode.put("staysLocal", setting.mode().staysLocal());
        mode.put("leavesLocal", setting.mode().leavesLocal());
        mode.put("audioLeavesDevice", setting.audioLeavesDevice());
        mode.put("allowRemoteFallback", setting.allowRemoteFallback());
        mode.put("updatedAt", setting.updatedAt() == null ? null : setting.updatedAt().toString());
        mode.put("updatedBy", setting.updatedBy());

        boolean interviewReady = transport.isEnabled() && !transport.validOrigins().isEmpty()
                && asrCaps.available();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("providers", providers);
        result.put("interviewVoice", Map.of(
                "enabled", interviewReady,
                "partialSupported", asrCaps.partialSupported(),
                "reason", interviewReady
                        ? (asrCaps.partialSupported() ? "实时语音面试可用" : "本地模式为按句转写，需校对后提交")
                        : "实时语音未启用：需开启语音通道、配置允许来源并让识别服务在线"));
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private static Map<String, Object> provider(String kind, String name, boolean available,
                                                boolean remote, boolean partialSupported, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", kind);
        item.put("provider", name);
        item.put("available", available);
        item.put("remote", remote);
        item.put("partialSupported", partialSupported);
        item.put("detail", detail);
        return item;
    }
}
