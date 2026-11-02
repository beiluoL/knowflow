package com.knowflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.capability.AiCapabilityException;
import com.knowflow.ai.capability.AiFailureKind;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CancellableStructuredStreamTest {
    @Test void deliversActualFramesBeforeDoneAndSuppressesLateFrames() {
        var tokens = new ArrayList<String>();
        var body = new CancellableStructuredStream.Subscriber(new ObjectMapper(), tokens::add, () -> false);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        frame(body, "data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n");
        assertEquals(List.of("first"), tokens);
        assertFalse(body.getBody().toCompletableFuture().isDone());
        frame(body, "data: {\"choices\":[{\"delta\":{\"content\":\"second\"}}]}\n\ndata: [DONE]\n\n");
        assertEquals("firstsecond", body.getBody().toCompletableFuture().join());
        frame(body, "data: {\"choices\":[{\"delta\":{\"content\":\"late\"}}]}\n\n");
        assertEquals(List.of("first", "second"), tokens);
        verify(subscription, atLeastOnce()).cancel();
    }
    @Test void cancellationAbortsSubscriptionAndNeverEmitsRemainingTokens() {
        var stopped = new AtomicBoolean();
        var tokens = new ArrayList<String>();
        var body = new CancellableStructuredStream.Subscriber(new ObjectMapper(), tokens::add, stopped::get);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        stopped.set(true);
        frame(body, "data: {\"choices\":[{\"delta\":{\"content\":\"private\"}}]}\n\n");
        var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> body.getBody().toCompletableFuture().join());
        assertEquals(AiFailureKind.CANCELLED, ((AiCapabilityException) failure.getCause()).getKind());
        assertTrue(tokens.isEmpty());
        verify(subscription, atLeastOnce()).cancel();
    }
    @Test void truncatedAndOversizedStreamsNeverBecomeSuccessful() {
        var truncated = new CancellableStructuredStream.Subscriber(new ObjectMapper(), token -> { }, () -> false);
        truncated.onSubscribe(mock(Flow.Subscription.class));
        frame(truncated, "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n");
        truncated.onComplete();
        assertTrue(truncated.getBody().toCompletableFuture().isCompletedExceptionally());
        var oversized = new CancellableStructuredStream.Subscriber(new ObjectMapper(), token -> fail(), () -> false);
        oversized.onSubscribe(mock(Flow.Subscription.class));
        frame(oversized, "x".repeat(131073));
        assertTrue(oversized.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    @Test void cancellationStopsEvenSilentUpstreamBeforeAnyFrame() throws Exception {
        try (var server = new okhttp3.mockwebserver.MockWebServer()) {
            server.start();
            server.enqueue(new okhttp3.mockwebserver.MockResponse()
                    .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));
            var stopped = new AtomicBoolean();
            var pending = java.util.concurrent.CompletableFuture.supplyAsync(() -> CancellableStructuredStream.invoke(
                    java.net.http.HttpClient.newHttpClient(), java.net.http.HttpRequest.newBuilder(
                            server.url("/stream").uri()).GET().build(), new ObjectMapper(), token -> fail(), stopped::get, 30));
            assertNotNull(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS));
            stopped.set(true);
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> pending.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(AiFailureKind.CANCELLED, ((AiCapabilityException) error.getCause()).getKind());
        }
    }

    private static void frame(CancellableStructuredStream.Subscriber body, String text) {
        body.onNext(List.of(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8))));
    }
}
