package com.knowflow.voice.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.voice.config.VoiceRuntimeProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * 本机 SenseVoiceSmall 转写客户端（批量、非流式）。
 * <p>只允许回环地址：本地模式的意义就是音频不出本机，允许配置远程地址等于
 * 把「本地」变成了欺骗性标签。服务不重试付费/昂贵请求，失败即上抛。
 */
@Component
public class LocalAsrClient {

    private static final String LOCALHOST = "127.0.0.1";
    private static final int MAX_RESPONSE_BYTES = 65536;
    private static final int MAX_TRANSCRIPT_CHARS = 12000;

    private final VoiceRuntimeProperties.LocalAsr config;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public LocalAsrClient(VoiceRuntimeProperties properties, ObjectMapper json) {
        this.config = properties.getLocalAsr();
        this.json = json;
    }

    /** 配置级就绪：开启、回环地址、token 非空、模型名非空。 */
    public boolean configured() {
        if (!config.isEnabled()) return false;
        if (config.getToken() == null || config.getToken().isBlank()) return false;
        if (config.getModel() == null || config.getModel().isBlank()) return false;
        return loopback(config.getEndpoint()) && loopback(config.getHealthEndpoint());
    }

    public record Health(boolean ok, boolean enabled, boolean loaded, long latencyMs, String detail) { }

    /** 真实探测：只读 /health，不触发模型加载、不产生费用。 */
    public Health health() {
        if (!configured()) return new Health(false, false, false, 0, "未启用或地址不是回环地址");
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
                return new Health(false, false, false, latency, "健康检查返回 " + response.statusCode());
            }
            JsonNode body = json.readTree(response.body());
            boolean enabled = body.path("enabled").asBoolean(false);
            boolean loaded = body.path("loaded").asBoolean(false);
            return new Health(enabled, enabled, loaded, latency,
                    loaded ? "模型已加载" : "服务在线，模型将在首次请求时加载");
        } catch (Exception unavailable) {
            return new Health(false, false, false,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), "本机 ASR 服务未运行");
        }
    }

    /** 上传整段 PCM16(16kHz 单声道)，返回原始转写文本。 */
    public String transcribe(byte[] pcm) {
        if (!configured()) throw new IllegalStateException("本地语音识别未启用");
        if (pcm == null || pcm.length == 0 || pcm.length % 2 != 0) {
            throw new IllegalArgumentException("音频数据无效");
        }
        int maxBytes = Math.max(1, config.getMaxSeconds()) * 32000;
        if (pcm.length > maxBytes) throw new IllegalArgumentException("音频超过时长限制");
        byte[] wav = wav(pcm);
        String boundary = "kf-" + UUID.randomUUID();
        try {
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"
                    + config.getModel() + "\r\n--" + boundary
                    + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n";
            ByteArrayOutputStream body = new ByteArrayOutputStream(wav.length + 512);
            body.write(head.getBytes(StandardCharsets.UTF_8));
            body.write(wav);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                    .timeout(Duration.ofSeconds(Math.max(5, Math.min(180, config.getTimeoutSeconds()))))
                    .header("Authorization", "Bearer " + config.getToken())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();
            CompletableFuture<HttpResponse<String>> pending =
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            HttpResponse<String> response;
            try {
                response = pending.get(Math.max(5, config.getTimeoutSeconds()), TimeUnit.SECONDS);
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
            if (response.statusCode() != 200) throw new IllegalStateException("本机语音识别失败");
            JsonNode root = json.readTree(response.body());
            JsonNode text = root.get("text");
            if (text == null || !text.isTextual()) throw new IllegalStateException("本机语音识别返回无效");
            String value = text.asText().trim();
            if (value.isEmpty() || value.length() > MAX_TRANSCRIPT_CHARS) {
                throw new IllegalStateException("本机语音识别返回无效");
            }
            return value;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("本机语音识别被中断");
        } catch (ExecutionException | TimeoutException | IOException failure) {
            throw new IllegalStateException("本机语音识别不可用");
        }
    }

    /** 16kHz / 16bit / 单声道 WAV 封装。 */
    static byte[] wav(byte[] pcm) {
        int dataLength = pcm.length;
        byte[] header = new byte[44];
        writeAscii(header, 0, "RIFF");
        writeInt(header, 4, 36 + dataLength);
        writeAscii(header, 8, "WAVE");
        writeAscii(header, 12, "fmt ");
        writeInt(header, 16, 16);
        writeShort(header, 20, 1);
        writeShort(header, 22, 1);
        writeInt(header, 24, 16000);
        writeInt(header, 28, 32000);
        writeShort(header, 32, 2);
        writeShort(header, 34, 16);
        writeAscii(header, 36, "data");
        writeInt(header, 40, dataLength);
        byte[] wav = new byte[44 + dataLength];
        System.arraycopy(header, 0, wav, 0, 44);
        System.arraycopy(pcm, 0, wav, 44, dataLength);
        return wav;
    }

    private static void writeAscii(byte[] target, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static void writeShort(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }

    private static boolean loopback(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return false;
        try {
            URI uri = URI.create(endpoint);
            return "http".equals(uri.getScheme())
                    && LOCALHOST.equals(uri.getHost())
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    static int maxResponseBytes() {
        return MAX_RESPONSE_BYTES;
    }
}
