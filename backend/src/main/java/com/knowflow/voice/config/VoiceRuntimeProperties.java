package com.knowflow.voice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音运行时配置。默认全部关闭，必须显式配置后才会向本机服务发请求。
 * <p>token 只用于本机回环服务鉴权，绝不返回前端、不写日志、不进 git。
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowflow.voice")
public class VoiceRuntimeProperties {

    /** 默认模式；数据库无实例级设置时使用。 */
    private String mode = "LOCAL";

    private LocalAsr localAsr = new LocalAsr();
    private LocalTts localTts = new LocalTts();
    private Ollama ollama = new Ollama();

    /** 能力探测缓存时间；避免每次拉取能力都打本机服务。 */
    private int capabilityCacheSeconds = 5;

    @Data
    public static class LocalAsr {
        private boolean enabled = false;
        private String endpoint = "http://127.0.0.1:8008/v1/audio/transcriptions";
        private String healthEndpoint = "http://127.0.0.1:8008/health";
        @lombok.ToString.Exclude
        private String token = "";
        private String model = "sensevoice-small";
        private int timeoutSeconds = 120;
        /** 单次上传 PCM 上限（秒），与本地服务 120 秒限制保持一致。 */
        private int maxSeconds = 120;
    }

    @Data
    public static class LocalTts {
        private boolean enabled = false;
        private String endpoint = "http://127.0.0.1:8010/v1/audio/speech";
        private String healthEndpoint = "http://127.0.0.1:8010/health";
        @lombok.ToString.Exclude
        private String token = "";
        private String voice = "melo";
        private float speed = 1.0f;
        private int timeoutSeconds = 120;
    }

    @Data
    public static class Ollama {
        private boolean enabled = false;
        private String baseUrl = "http://127.0.0.1:11434";
        private String model = "qwen3.5:9b";
        /** 语音链路必须关思考，否则首字延迟不可控甚至只产出 reasoning。 */
        private boolean think = false;
        private int maxTokens = 512;
        private int timeoutSeconds = 120;

        public boolean usable() {
            return enabled && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }
    }
}
