package com.knowflow.interview.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 有界且不重试的 WebSocket 传输，载荷和上游错误均不写入日志。 */
final class QwenSpeechSocket implements WebSocket.Listener {
    private static final int MAX_MESSAGE_CHARS = 262144;
    // 复用TLS/HTTP选择器资源；并发连接另由语音票据和有界会话队列限制。
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Set<String> HOSTS = Set.of("dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com",
            "dashscope-us.aliyuncs.com");
    private final ObjectMapper json;
    private final Consumer<JsonNode> events;
    private final Consumer<String> errors;
    private final StringBuilder incoming = new StringBuilder();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final int timeout;
    private final Object sendLock = new Object();
    private WebSocket socket;
    private boolean closed;

    QwenSpeechSocket(ObjectMapper json, Consumer<JsonNode> events, Consumer<String> errors, int timeout) {
        this.json = json;
        this.events = events;
        this.errors = errors;
        this.timeout = Math.max(1, Math.min(180, timeout));
    }

    static boolean configured(RealtimeSpeechProperties all, RealtimeSpeechProperties.Endpoint config) {
        if (!all.isEnabled() || !config.isEnabled() || config.getApiKey() == null
                || config.getApiKey().isBlank() || config.getApiKey().length() > 4096
                || config.getApiKey().chars().anyMatch(c -> c < 33 || c > 126) || config.getModel() == null
                || !config.getModel().matches("[a-zA-Z0-9._-]{1,128}")) { return false; }
        try {
            URI uri = URI.create(config.getEndpoint());
            return "wss".equals(uri.getScheme()) && HOSTS.contains(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null
                    && uri.getRawQuery() == null && uri.getFragment() == null
                    && "/api-ws/v1/realtime".equals(uri.getRawPath());
        } catch (IllegalArgumentException | NullPointerException ex) { return false; }
    }

    void connect(RealtimeSpeechProperties.Endpoint config, Map<String, Object> session) {
        try {
            URI uri = URI.create(config.getEndpoint() + "?model="
                    + URLEncoder.encode(config.getModel(), StandardCharsets.UTF_8));
            var pending = HTTP_CLIENT.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(timeout))
                    .header("Authorization", "Bearer " + config.getApiKey()).buildAsync(uri, this);
            WebSocket connected;
            try { connected = pending.get(timeout, TimeUnit.SECONDS); }
            finally { if (!pending.isDone()) { pending.cancel(true); } }
            synchronized (this) {
                if (closed) { connected.abort(); return; }
                socket = connected;
            }
            send("session.update", Map.of("session", session));
            ready.get(timeout, TimeUnit.SECONDS);
            CompletableFuture.delayedExecutor(timeout, TimeUnit.SECONDS).execute(() -> fail("SPEECH_TIMEOUT"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail("SPEECH_UNAVAILABLE");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException
                | IllegalArgumentException ex) {
            fail("SPEECH_UNAVAILABLE");
        }
    }

    void send(String type, Map<String, Object> fields) {
        synchronized (sendLock) {
            WebSocket target;
            synchronized (this) {
                if (closed) { return; }
                target = socket;
            }
            try {
                Map<String, Object> event = new LinkedHashMap<>(fields);
                event.put("type", type);
                event.put("event_id", UUID.randomUUID().toString());
                String encoded = json.writeValueAsString(event);
                if (encoded.length() > MAX_MESSAGE_CHARS) { fail("SPEECH_LIMIT"); return; }
                target.sendText(encoded, true).get(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail("SPEECH_UNAVAILABLE");
            } catch (java.io.IOException | java.util.concurrent.ExecutionException
                    | java.util.concurrent.TimeoutException | IllegalStateException ex) {
                fail("SPEECH_UNAVAILABLE");
            }
        }
    }

    synchronized void cancel() {
        closed = true;
        ready.completeExceptionally(new IllegalStateException("Speech closed"));
        if (socket != null) { socket.abort(); }
        incoming.setLength(0);
    }

    synchronized void fail(String code) {
        if (!closed) {
            cancel();
            errors.accept(code);
        }
    }

    @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }

    @Override public synchronized CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        if (!closed) {
            if (data.length() > MAX_MESSAGE_CHARS - incoming.length()) { fail("SPEECH_LIMIT"); }
            else {
                incoming.append(data);
                if (last) {
                    try {
                        JsonNode event = json.readTree(incoming.toString());
                        incoming.setLength(0);
                        String type = event.path("type").asText();
                        if ("session.updated".equals(type)) { ready.complete(null); }
                        else if ("error".equals(type) || type.endsWith(".failed")) { fail("SPEECH_PROVIDER_ERROR"); }
                        else { events.accept(event); }
                    } catch (java.io.IOException | IllegalArgumentException ex) { fail("SPEECH_PROTOCOL_ERROR"); }
                }
            }
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<?> onBinary(WebSocket webSocket, java.nio.ByteBuffer data, boolean last) {
        fail("SPEECH_PROTOCOL_ERROR");
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        fail("SPEECH_CONNECTION_CLOSED");
        return CompletableFuture.completedFuture(null);
    }

    @Override public void onError(WebSocket webSocket, Throwable error) { fail("SPEECH_UNAVAILABLE"); }
}
