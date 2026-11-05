package com.knowflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class AiTransportSafetyTest {
  @Test
  void retainsStatusWithoutRetainingProviderErrorBody() {
    var upstream =
        HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "private-provider-detail",
            HttpHeaders.EMPTY,
            "private-response-body".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    var failure = AiTransportErrors.from("模型调用失败", upstream);
    assertEquals("模型调用失败：模型返回状态码 429", failure.getMessage());
    assertNull(failure.getCause());
  }

  @Test
  void rejectsUpstreamHttpErrorOnceWithoutPublishingItsBody() throws Exception {
    try (var server = new okhttp3.mockwebserver.MockWebServer()) {
      server.start();
      server.enqueue(
          new okhttp3.mockwebserver.MockResponse()
              .setResponseCode(429)
              .setBody("private-response-body"));
      var errors = new java.util.concurrent.atomic.AtomicInteger();
      var failure =
          assertThrows(
              AiException.class,
              () ->
                  SseClient.stream(
                      server.url("/stream").toString(),
                      null,
                      java.util.Map.of(),
                      new ObjectMapper(),
                      new SseClient.Handler() {
                        @Override
                        public void onData(JsonNode frame) {
                          throw new AssertionError("unexpected data frame");
                        }

                        @Override
                        public void onError(String message) {
                          errors.incrementAndGet();
                          throw new AiException(message);
                        }
                      }));
      assertEquals(1, errors.get());
      assertEquals("模型返回状态码 429", failure.getMessage());
    }
  }

  @Test
  void malformedFramesAreSkippedButConsumerFailureStopsAndClosesStream() {
    var closed = new AtomicBoolean();
    var stream =
        new ByteArrayInputStream(
            "data: malformed\ndata: {\"delta\":\"ok\"}\ndata: [DONE]\n"
                .getBytes(StandardCharsets.UTF_8)) {
          @Override
          public void close() {
            closed.set(true);
          }
        };
    var cancelled = new IllegalStateException("consumer cancelled");
    var handler =
        new SseClient.Handler() {
          @Override
          public void onData(JsonNode frame) {
            assertEquals("ok", frame.path("delta").asText());
            throw cancelled;
          }

          @Override
          public void onError(String message) {
            throw new AssertionError(message);
          }
        };
    assertSame(
        cancelled,
        assertThrows(
            IllegalStateException.class,
            () -> SseClient.readFrames(stream, false, new ObjectMapper(), handler)));
    assertTrue(closed.get());
  }
}
