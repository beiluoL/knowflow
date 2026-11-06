package com.knowflow.voice.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.voice.config.VoiceRuntimeProperties;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * 本机 sherpa-onnx TTS 客户端。服务以 NDJSON 分块返回 PCM16，
 * 分块大小由模型自身决定，不保证「边合成边出声」，前端只按到达顺序排队播放。
 * <p>只允许回环地址，本地模式不允许把回答文本发给远程合成服务。
 */
@Component
public class LocalTtsClient {

    private static final int MAX_TEXT_CHARS = 2000;
    private static final int MAX_AUDIO_BYTES = 5760000;

    private final VoiceRuntimeProperties.LocalTts config;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public LocalTtsClient(VoiceRuntimeProperties properties, ObjectMapper json) {
        this.config = properties.getLocalTts();
        this.json = json;
    }

    public boolean configured() {
        if (!config.isEnabled()) return false;
        if (config.getToken() == null || config.getToken().isBlank()) return false;
        return loopback(config.getEndpoint()) && loopback(config.getHealthEndpoint());
    }

    public String voice() {
        return config.getVoice();
    }

    public float speed() {
        return config.getSpeed();
    }

    public record Health(boolean ok, boolean enabled, boolean loaded, long latencyMs, String detail,
                         int sampleRate, String engine) { }

    public Health health() {
        if (!configured()) return new Health(false, false, false, 0, "未启用或地址不是回环地址", 0, "");
        long started = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(config.getHealthEndpoint()))
                            .timeout(Duration.ofMillis(1500))
                            .header("Authorization", "Bearer " + config.getToken())
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (response.statusCode() != 200) {
                return new Health(false, false, false, latency, "健康检查返回 " + response.statusCode(), 0, "");
            }
            JsonNode body = json.readTree(response.body());
            boolean enabled = body.path("enabled").asBoolean(false);
            boolean loaded = body.path("loaded").asBoolean(false);
            int sampleRate = body.path("sampleRate").asInt(0);
            String engine = body.path("engine").asText("");
            return new Health(enabled, enabled, loaded, latency,
                    (loaded ? "模型已加载" : "服务在线，模型将在首次请求时加载")
                            + (engine.isBlank() ? "" : "（引擎 " + engine + "）"), sampleRate, engine);
        } catch (Exception unavailable) {
            return new Health(false, false, false,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), "本机 TTS 服务未运行", 0, "");
        }
    }

    public record Chunk(byte[] pcm, int sampleRate, long sequence, boolean last) { }

    /**
     * 流式取音频；调用方线程会被阻塞直到流结束，必须放在自己的工作线程里。
     * @return 实际采样率
     */
    public int stream(String text, Consumer<Chunk> sink) throws Exception {
        if (!configured()) throw new IllegalStateException("本地语音合成未启用");
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("合成文本无效");
        }
        String body = json.writeValueAsString(new Request(text, config.getVoice(), config.getSpeed()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                .timeout(Duration.ofSeconds(Math.max(10, Math.min(180, config.getTimeoutSeconds()))))
                .header("Authorization", "Bearer " + config.getToken())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (InputStream ignored = response.body()) {
                throw new IllegalStateException("本机语音合成失败");
            }
        }
        int sampleRate = 0;
        long total = 0;
        try (InputStream input = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node = json.readTree(line);
                int rate = node.path("sampleRate").asInt(0);
                if (rate > 0) sampleRate = rate;
                String audio = node.path("audio").asText("");
                boolean last = node.path("last").asBoolean(false);
                byte[] pcm = audio.isEmpty() ? new byte[0] : Base64.getDecoder().decode(audio);
                if (pcm.length % 2 != 0) throw new IllegalStateException("合成音频帧无效");
                total += pcm.length;
                if (total > MAX_AUDIO_BYTES) throw new IllegalStateException("合成音频超过限制");
                sink.accept(new Chunk(pcm, sampleRate, node.path("sequence").asLong(0), last));
                if (last) break;
            }
        }
        if (sampleRate <= 0) throw new IllegalStateException("合成未返回采样率");
        return sampleRate;
    }

    private record Request(String text, String voice, float speed) { }

    private static boolean loopback(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return false;
        try {
            URI uri = URI.create(endpoint);
            return "http".equals(uri.getScheme())
                    && "127.0.0.1".equals(uri.getHost())
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
