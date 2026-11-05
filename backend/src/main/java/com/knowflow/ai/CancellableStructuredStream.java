package com.knowflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.capability.AiCapabilityException;
import com.knowflow.ai.capability.AiFailureKind;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 有界的 OpenAI 兼容 SSE 流，即使供应方未发送字节也支持主动取消。 */
public final class CancellableStructuredStream {
    private CancellableStructuredStream() { }

    public static String invoke(HttpClient client, HttpRequest request, ObjectMapper json,
            Consumer<String> delta, BooleanSupplier cancelled, int timeoutSeconds) {
        var body = new Subscriber(json, delta, cancelled);
        var pending = client.sendAsync(request, info -> {
            if (info.statusCode() != 200) {
                body.reject(info.statusCode() == 401 || info.statusCode() == 403
                        ? AiFailureKind.INVALID_KEY : AiFailureKind.INVALID_RESPONSE);
            }
            return body;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, Math.min(180, timeoutSeconds)));
        try {
            while (true) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                    throw new AiCapabilityException(AiFailureKind.CANCELLED, null);
                }
                if (System.nanoTime() >= deadline) {
                    // 结构化流已经部分输出后，不再重试或重放。
                    throw new AiCapabilityException(AiFailureKind.INVALID_RESPONSE, "Structured stream deadline");
                }
                try { return pending.get(100, TimeUnit.MILLISECONDS).body(); }
                catch (TimeoutException waiting) { /* 仅轮询停止信号和截止时间，不重试供应方请求。 */ }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiCapabilityException(AiFailureKind.CANCELLED, null);
        } catch (java.util.concurrent.ExecutionException failed) {
            if (failed.getCause() instanceof AiCapabilityException classified) { throw classified; }
            throw new AiCapabilityException(AiFailureKind.INVALID_RESPONSE, "Structured stream failed");
        } finally {
            body.abort();
            if (!pending.isDone()) { pending.cancel(true); }
        }
    }

    static final class Subscriber implements HttpResponse.BodySubscriber<String> {
        private static final int MAX_LINE_BYTES = 131072;
        private static final int MAX_BODY_BYTES = 2097152;
        private static final int MAX_CONTENT_CHARS = 120000;
        private final ObjectMapper json;
        private final Consumer<String> delta;
        private final BooleanSupplier cancelled;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private final StringBuilder content = new StringBuilder();
        private Flow.Subscription subscription;
        private int received;
        private boolean finished;

        Subscriber(ObjectMapper json, Consumer<String> delta, BooleanSupplier cancelled) {
            this.json = json; this.delta = delta; this.cancelled = cancelled;
        }
        @Override public CompletionStage<String> getBody() { return result; }
        @Override public synchronized void onSubscribe(Flow.Subscription value) {
            subscription = value;
            if (result.isDone()) { value.cancel(); } else { value.request(1); }
        }
        @Override public synchronized void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) { return; }
            try {
                for (ByteBuffer buffer : buffers) {
                    while (buffer.hasRemaining() && !result.isDone()) {
                        if (cancelled.getAsBoolean()) { reject(AiFailureKind.CANCELLED); return; }
                        if (++received > MAX_BODY_BYTES) { reject(AiFailureKind.INVALID_RESPONSE); return; }
                        byte value = buffer.get();
                        if (value == '\n') { processLine(); line.reset(); }
                        else if (line.size() >= MAX_LINE_BYTES) { reject(AiFailureKind.INVALID_RESPONSE); return; }
                        else { line.write(value); }
                    }
                }
                if (!result.isDone()) { subscription.request(1); }
            } catch (java.io.IOException | RuntimeException invalid) { reject(AiFailureKind.INVALID_RESPONSE); }
        }
        private void processLine() throws java.io.IOException {
            String value = line.toString(StandardCharsets.UTF_8).trim();
            if (!value.startsWith("data:")) { return; }
            value = value.substring(5).trim();
            if ("[DONE]".equals(value)) {
                if (content.isEmpty()) { reject(AiFailureKind.INVALID_RESPONSE); return; }
                finished = true; result.complete(content.toString()); abort(); return;
            }
            var root = json.readTree(value);
            if (root.has("error")) { reject(AiFailureKind.INVALID_RESPONSE); return; }
            String token = root.path("choices").path(0).path("delta").path("content").asText("");
            if (token.length() > MAX_CONTENT_CHARS - content.length()) { reject(AiFailureKind.INVALID_RESPONSE); return; }
            if (!token.isEmpty()) { content.append(token); delta.accept(token); }
        }
        synchronized void reject(AiFailureKind kind) {
            result.completeExceptionally(new AiCapabilityException(kind, "Structured stream rejected")); abort();
        }
        synchronized void abort() {
            if (!result.isDone()) {
                result.completeExceptionally(new AiCapabilityException(AiFailureKind.CANCELLED, null));
            }
            if (subscription != null) { subscription.cancel(); }
        }
        @Override public synchronized void onError(Throwable failed) { reject(AiFailureKind.INVALID_RESPONSE); }
        @Override public synchronized void onComplete() {
            if (!finished) { reject(AiFailureKind.INVALID_RESPONSE); }
        }
    }
}
