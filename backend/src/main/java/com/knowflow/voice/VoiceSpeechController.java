package com.knowflow.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.speech.InterviewAsrProvider;
import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.voice.asr.LocalAsrClient;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 语音实验台的「听」与「说」两个原子能力。
 * <p>这里只做转写与合成，**不**建立第二套聊天业务：文本仍由既有
 * {@code POST /api/chat/stream} 处理（会话、消息、RAG、Tutor 全部复用）。
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceSpeechController {

    private static final Logger log = LoggerFactory.getLogger(VoiceSpeechController.class);
    private static final int MAX_AUDIO_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SECONDS = 60;
    private static final int MAX_SPEAK_CHARS = 2000;

    private final VoiceRuntimeSettingsService settings;
    private final LocalAsrClient localAsr;
    private final InterviewAsrProvider cloudAsr;
    private final InterviewTtsProvider tts;
    private final ObjectMapper json;

    public VoiceSpeechController(VoiceRuntimeSettingsService settings, LocalAsrClient localAsr,
                                 InterviewAsrProvider cloudAsr, InterviewTtsProvider tts, ObjectMapper json) {
        this.settings = settings;
        this.localAsr = localAsr;
        this.cloudAsr = cloudAsr;
        this.tts = tts;
        this.json = json;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> transcribe(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请提供录音");
        if (file.getSize() > MAX_AUDIO_BYTES) throw new BusinessException(413, "录音超过限制");
        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException unreadable) {
            throw new BusinessException(400, "无法读取录音");
        }
        PcmWav.Audio audio;
        try {
            audio = PcmWav.parse(raw, MAX_SECONDS);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(400, invalid.getMessage());
        }
        VoiceRuntimeSetting runtime = settings.current();
        long started = System.nanoTime();
        String text;
        String provider;
        try {
            if (runtime.mode() == VoiceMode.CLOUD) {
                if (!cloudAsr.capability()) throw new BusinessException(503, "云端识别未配置，请改用本地模式或文字输入");
                text = cloudAsr.transcribe(PcmWav.wrap(audio.pcm()));
                provider = cloudAsr.auditName();
            } else {
                if (!localAsr.configured()) throw new BusinessException(503, "本地识别未启用，请先启动本机语音服务");
                text = localAsr.transcribe(audio.pcm());
                provider = "LOCAL_SENSEVOICE";
            }
        } catch (IllegalStateException failure) {
            throw new BusinessException(503, failure.getMessage());
        } finally {
            java.util.Arrays.fill(audio.pcm(), (byte) 0);
        }
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        log.info("VOICE_TRANSCRIBE user={} provider={} audioMs={} latencyMs={}", userId, provider, audio.durationMs(), latencyMs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("provider", provider);
        result.put("audioMs", audio.durationMs());
        result.put("latencyMs", latencyMs);
        result.put("at", Instant.now().toString());
        return Result.success(result);
    }

    public record SpeakRequest(String text, Float speed) { }

    /** 逐句回传 PCM16 NDJSON；客户端可随时中断请求实现打断。 */
    @PostMapping(value = "/speak", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> speak(@RequestBody SpeakRequest request) {
        SecurityUtils.getCurrentUserId();
        String text = request == null ? null : request.text();
        if (text == null || text.isBlank()) throw new BusinessException(400, "缺少待合成文本");
        if (text.length() > MAX_SPEAK_CHARS) throw new BusinessException(400, "合成文本过长");
        var capabilities = tts.capabilities();
        if (!capabilities.available()) throw new BusinessException(503, "语音合成未启用或服务未运行");

        StreamingResponseBody body = output -> stream(text.trim(), output);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson; charset=utf-8"))
                .header("Cache-Control", "no-store")
                .body(body);
    }

    private void stream(String text, OutputStream output) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        String requestId = java.util.UUID.randomUUID().toString();
        InterviewTtsProvider.Stream stream;
        try {
            stream = tts.synthesizeStream(text, requestId, new InterviewTtsProvider.Listener() {
                @Override
                public void audio(byte[] pcm, String format, int sampleRate, long sequence, boolean last) {
                    try {
                        write(output, sequence, sampleRate, pcm, last);
                    } catch (IOException disconnect) {
                        failure.set(disconnect);
                    } finally {
                        if (last) done.countDown();
                    }
                }

                @Override
                public void error(String code) {
                    failure.set(new IllegalStateException(code));
                    done.countDown();
                }
            });
        } catch (RuntimeException unavailable) {
            throw unavailable;
        }
        try {
            if (!done.await(120, TimeUnit.SECONDS)) {
                stream.cancel();
                failure.compareAndSet(null, new IllegalStateException("TTS_TIMEOUT"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            stream.cancel();
        } finally {
            stream.cancel();
        }
        if (failure.get() instanceof IOException) return;
        if (failure.get() != null) {
            throw new IllegalStateException("语音合成未完成");
        }
    }

    private void write(OutputStream output, long sequence, int sampleRate, byte[] pcm, boolean last) throws IOException {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("sequence", sequence);
        line.put("sampleRate", sampleRate);
        line.put("audio", pcm == null || pcm.length == 0 ? "" : Base64.getEncoder().encodeToString(pcm));
        line.put("last", last);
        output.write(json.writeValueAsBytes(line));
        output.write('\n');
        output.flush();
    }
}
