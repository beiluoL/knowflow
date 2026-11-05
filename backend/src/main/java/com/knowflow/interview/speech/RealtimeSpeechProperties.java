package com.knowflow.interview.speech;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 必须显式启用；凭据仅在服务端使用，不输出到诊断信息。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "knowflow.interview.realtime-speech")
public class RealtimeSpeechProperties {
    private boolean enabled;
    private Endpoint asr = new Endpoint();
    private Endpoint tts = new Endpoint();
    private int timeoutSeconds = 120;
    private String voice = "Cherry";
    /** 服务端 VAD 灵敏度，必须为 [0, 1] 内有限数值。 */
    private double vadThreshold = 0.5;
    /** VAD 仅划分语句片段，只有显式结束才完成回答。 */
    private int vadSilenceDurationMs = 800;

    /** 端点为不含模型查询参数的基础 WebSocket URI。 */
    @Data
    public static class Endpoint {
        private boolean enabled;
        private String endpoint = "";
        @lombok.ToString.Exclude
        private String apiKey = "";
        private String model = "";
    }
}
