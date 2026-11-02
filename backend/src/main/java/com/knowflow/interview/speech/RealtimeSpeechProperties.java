package com.knowflow.interview.speech;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Explicit opt-in; credentials are server-only and excluded from diagnostic output. */
@Data
@Configuration
@ConfigurationProperties(prefix = "knowflow.interview.realtime-speech")
public class RealtimeSpeechProperties {
    private boolean enabled;
    private Endpoint asr = new Endpoint();
    private Endpoint tts = new Endpoint();
    private int timeoutSeconds = 120;
    private String voice = "Cherry";
    /** Server VAD sensitivity, finite and within [0, 1]. */
    private double vadThreshold = 0.5;
    /** VAD creates utterance segments; only explicit finish completes the answer. */
    private int vadSilenceDurationMs = 800;

    /** Endpoint is the base WebSocket URI without model query parameters. */
    @Data
    public static class Endpoint {
        private boolean enabled;
        private String endpoint = "";
        @lombok.ToString.Exclude
        private String apiKey = "";
        private String model = "";
    }
}
