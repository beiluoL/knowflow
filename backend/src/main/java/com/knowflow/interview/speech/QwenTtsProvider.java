package com.knowflow.interview.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Qwen streaming synthesis; raw signed PCM16 little-endian mono at 24 kHz. */
@Component
public class QwenTtsProvider implements InterviewTtsProvider {
    private static final int SAMPLE_RATE = 24000;
    private static final int MAX_TEXT_CHARS = 6000;
    private final RealtimeSpeechProperties config;
    private final ObjectMapper json;

    public QwenTtsProvider(RealtimeSpeechProperties config, ObjectMapper json) {
        this.config = config;
        this.json = json;
    }

    @Override public SpeechStreamCapabilities capabilities() {
        return new SpeechStreamCapabilities(QwenSpeechSocket.configured(config, config.getTts())
                && config.getVoice() != null && !config.getVoice().isBlank() && config.getVoice().length() <= 128
                && config.getVoice().chars().noneMatch(Character::isISOControl),
                "QWEN_REALTIME_TTS", "PCM16", SAMPLE_RATE);
    }

    @Override public Stream synthesizeStream(String text, String requestId, Listener listener) {
        if (!capabilities().available()) { throw new IllegalStateException("Streaming TTS unavailable"); }
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("TTS text rejected");
        }
        var stream = new TtsStream(listener);
        stream.connection.connect(config.getTts(), Map.of("voice", config.getVoice(), "mode", "commit",
                "response_format", "pcm", "sample_rate", SAMPLE_RATE));
        stream.connection.send("input_text_buffer.append", Map.of("text", text));
        stream.connection.send("input_text_buffer.commit", Map.of());
        stream.connection.send("session.finish", Map.of());
        return stream;
    }

    private final class TtsStream implements Stream {
        private final QwenSpeechSocket connection;
        private final AtomicLong sequence = new AtomicLong();
        private long received;
        TtsStream(Listener listener) {
            connection = new QwenSpeechSocket(json, event -> {
                String type = event.path("type").asText();
                if ("response.audio.delta".equals(type)) {
                    byte[] pcm = Base64.getDecoder().decode(event.path("delta").asText());
                    received += pcm.length;
                    if (received > 5760000L || pcm.length % 2 != 0) { reject(); return; }
                    listener.audio(pcm, "PCM16", SAMPLE_RATE, sequence.getAndIncrement(), false);
                } else if ("session.finished".equals(type)) {
                    listener.audio(new byte[0], "PCM16", SAMPLE_RATE, sequence.getAndIncrement(), true);
                    cancel();
                }
            }, listener::error, config.getTimeoutSeconds());
        }
        private void reject() { connection.fail("SPEECH_AUDIO_LIMIT"); }
        @Override public void cancel() { connection.cancel(); }
    }
}
