package com.knowflow.voice.asr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.voice.config.VoiceRuntimeProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 适配器契约：接口是流式的，能力不是——只产出 final，且 partialSupported=false。
 * 用真实 HTTP 桩服务验证编码、令牌、错误映射，不伪造「模型已识别」的结论。
 */
class LocalBatchStreamingAsrProviderTest {

    private HttpServer server;
    private LocalBatchStreamingAsrProvider provider;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile int transcribeStatus = 200;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/health", exchange -> {
            byte[] body = "{\"enabled\":true,\"loaded\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/v1/audio/transcriptions", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(new String(body, StandardCharsets.ISO_8859_1));
            if (transcribeStatus != 200) {
                exchange.sendResponseHeaders(transcribeStatus, -1);
                exchange.close();
                return;
            }
            byte[] response = "{\"text\":\"这是本地识别结果\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();

        VoiceRuntimeProperties properties = new VoiceRuntimeProperties();
        var asr = properties.getLocalAsr();
        asr.setEnabled(true);
        asr.setToken("test-token");
        asr.setModel("sensevoice-small");
        asr.setEndpoint("http://127.0.0.1:" + port + "/v1/audio/transcriptions");
        asr.setHealthEndpoint("http://127.0.0.1:" + port + "/health");
        asr.setTimeoutSeconds(10);
        provider = new LocalBatchStreamingAsrProvider(new LocalAsrClient(properties, new ObjectMapper()), properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void reportsHonestCapabilities() {
        var capabilities = provider.capabilities();
        assertTrue(capabilities.available());
        assertEquals("LOCAL_SENSEVOICE", capabilities.provider());
        assertFalse(capabilities.partialSupported(), "本地批量识别不得声明支持增量转写");
    }

    @Test
    void onlyEmitsFinalAfterFinish() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<String> partials = new CopyOnWriteArrayList<>();
        List<String> finals = new CopyOnWriteArrayList<>();
        var stream = provider.open("req-1", new com.knowflow.interview.speech.InterviewStreamingAsrProvider.Listener() {
            public void partial(String text) { partials.add(text); }
            public void finalText(String text) { finals.add(text); }
            public void speechStarted() { }
            public void speechEnded() { }
            public void completed() { done.countDown(); }
            public void error(String code) { done.countDown(); }
        });
        byte[] frame = new byte[6400]; // 200ms
        stream.audio(frame);
        assertEquals(0, finals.size(), "未 finish 前不应返回最终文本");
        stream.finish();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("这是本地识别结果"), finals);
        assertTrue(partials.isEmpty(), "批量识别不产出 partial");
        assertEquals(1, requests.size());
        assertTrue(requests.get(0).contains("name=\"model\""), "必须是 multipart 字段 model");
        assertTrue(requests.get(0).contains("RIFF"), "上传体必须是 WAV");
    }

    @Test
    void cancelDropsResult() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<String> finals = new CopyOnWriteArrayList<>();
        var stream = provider.open("req-2", new com.knowflow.interview.speech.InterviewStreamingAsrProvider.Listener() {
            public void partial(String text) { }
            public void finalText(String text) { finals.add(text); }
            public void speechStarted() { }
            public void speechEnded() { }
            public void completed() { done.countDown(); }
            public void error(String code) { done.countDown(); }
        });
        stream.audio(new byte[6400]);
        stream.cancel();
        stream.finish();
        done.await(300, TimeUnit.MILLISECONDS);
        assertTrue(finals.isEmpty(), "取消后不得回传结果");
    }

    @Test
    void mapsProviderFailureToUnavailable() throws Exception {
        transcribeStatus = 503;
        CountDownLatch done = new CountDownLatch(1);
        List<String> errors = new CopyOnWriteArrayList<>();
        var stream = provider.open("req-3", new com.knowflow.interview.speech.InterviewStreamingAsrProvider.Listener() {
            public void partial(String text) { }
            public void finalText(String text) { }
            public void speechStarted() { }
            public void speechEnded() { }
            public void completed() { done.countDown(); }
            public void error(String code) { errors.add(code); done.countDown(); }
        });
        stream.audio(new byte[6400]);
        stream.finish();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("ASR_UNAVAILABLE"), errors);
    }

    @Test
    void rejectsUnconfiguredProvider() {
        VoiceRuntimeProperties off = new VoiceRuntimeProperties();
        var disabled = new LocalBatchStreamingAsrProvider(
                new LocalAsrClient(off, new ObjectMapper()), off);
        assertFalse(disabled.capabilities().available());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> disabled.open("req-4", new com.knowflow.interview.speech.InterviewStreamingAsrProvider.Listener() {
                    public void partial(String text) { }
                    public void finalText(String text) { }
                    public void speechStarted() { }
                    public void speechEnded() { }
                    public void error(String code) { }
                }));
    }
}
