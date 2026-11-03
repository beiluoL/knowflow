package com.knowflow.interview.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Qwen ASR realtime PCM16 16 kHz with server VAD; independent of batch ASR. */
@Component("qwenStreamingAsrProvider")
public class QwenStreamingAsrProvider implements InterviewStreamingAsrProvider {
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_CHUNK_BYTES = 32000;
    private static final long MAX_AUDIO_BYTES = 3840000L;
    private final RealtimeSpeechProperties config;
    private final ObjectMapper json;

    public QwenStreamingAsrProvider(RealtimeSpeechProperties config, ObjectMapper json) {
        this.config = config;
        this.json = json;
    }

    @Override public SpeechStreamCapabilities capabilities() {
        return new SpeechStreamCapabilities(QwenSpeechSocket.configured(config, config.getAsr())
                && Double.isFinite(config.getVadThreshold()) && config.getVadThreshold() >= 0
                && config.getVadThreshold() <= 1 && config.getVadSilenceDurationMs() >= 200
                && config.getVadSilenceDurationMs() <= 5000,
                "QWEN_REALTIME_ASR", "PCM16", SAMPLE_RATE);
    }

    @Override public Stream open(String requestId, Listener listener) {
        if (!capabilities().available()) { throw new IllegalStateException("Streaming ASR unavailable"); }
        var stream = new AsrStream(listener);
        stream.connection.connect(config.getAsr(), Map.of("input_audio_format", "pcm", "sample_rate", SAMPLE_RATE,
                "input_audio_transcription", Map.of("language", "zh"),
                "turn_detection", Map.of("type", "server_vad", "threshold", config.getVadThreshold(),
                        "silence_duration_ms", config.getVadSilenceDurationMs())));
        return stream;
    }

    final class AsrStream implements Stream {
        final QwenSpeechSocket connection;
        private static final int MAX_FINAL_ITEMS = 256;
        private final Set<String> finalItems = new HashSet<>();
        private long received;
        private boolean finished;
        AsrStream(Listener listener) {
            connection = new QwenSpeechSocket(json, event -> {
                switch (event.path("type").asText()) {
                    case "conversation.item.input_audio_transcription.text" -> listener.partial(
                            event.path("text").asText() + event.path("stash").asText());
                    case "conversation.item.input_audio_transcription.completed" -> {
                        String itemId = event.path("item_id").asText();
                        if (itemId.isBlank()) { itemId = event.path("event_id").asText(); }
                        if (itemId.isBlank() || itemId.length() > 256) { reject("SPEECH_PROTOCOL_ERROR"); return; }
                        if (finalItems.contains(itemId)) { return; }
                        if (finalItems.size() >= MAX_FINAL_ITEMS) { reject("SPEECH_LIMIT"); return; }
                        finalItems.add(itemId);
                        listener.finalText(event.path("transcript").asText());
                    }
                    case "input_audio_buffer.speech_started" -> listener.speechStarted();
                    case "input_audio_buffer.speech_stopped" -> listener.speechEnded();
                    case "session.finished" -> { listener.completed(); cancel(); }
                    default -> { }
                }
            }, listener::error, config.getTimeoutSeconds());
        }
        private void reject(String code) { connection.fail(code); }
        @Override public synchronized void audio(byte[] pcm) {
            if (finished) { return; }
            if (pcm == null || pcm.length == 0 || pcm.length > MAX_CHUNK_BYTES || pcm.length % 2 != 0
                    || pcm.length > MAX_AUDIO_BYTES - received) {
                connection.fail("SPEECH_AUDIO_LIMIT");
                return;
            }
            received += pcm.length;
            connection.send("input_audio_buffer.append", Map.of("audio", Base64.getEncoder().encodeToString(pcm)));
        }
        @Override public synchronized void finish() {
            if (!finished) { finished = true; connection.send("session.finish", Map.of()); }
        }
        @Override public void cancel() { connection.cancel(); }
    }
}
