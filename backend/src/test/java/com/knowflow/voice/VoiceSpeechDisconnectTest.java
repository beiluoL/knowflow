package com.knowflow.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.interview.speech.SpeechStreamCapabilities;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.knowflow.voice.controller.VoiceSpeechController;
/**
 * 验证语音连接断开时的处理行为。
 */
class VoiceSpeechDisconnectTest {
  @Test
  void disconnectOnNonFinalFrameCancelsProviderWithoutWaitingForFinalFrame() {
    var cancellations = new AtomicInteger();
    InterviewTtsProvider provider =
        new InterviewTtsProvider() {
          @Override
          public SpeechStreamCapabilities capabilities() {
            return new SpeechStreamCapabilities(true, "test", "pcm16", 16000);
          }

          @Override
          public Stream synthesizeStream(String text, String requestId, Listener listener) {
            listener.audio(new byte[] {1, 2}, "pcm16", 16000, 0, false);
            return cancellations::incrementAndGet;
          }
        };
    var controller = new VoiceSpeechController(null, null, null, provider, new ObjectMapper());
    SecurityContextHolder.getContext()
        .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    try {
      var body = controller.speak(new VoiceSpeechController.SpeakRequest("hello", null)).getBody();
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () ->
              body.writeTo(
                  new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                      throw new IOException("client disconnected");
                    }
                  }));
      assertEquals(1, cancellations.get());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
