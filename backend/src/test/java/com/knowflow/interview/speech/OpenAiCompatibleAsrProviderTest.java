package com.knowflow.interview.speech;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OpenAiCompatibleAsrProviderTest {
 @Test void defaultOffHasNoUsableCapability(){var p=new OpenAiCompatibleAsrProvider(new SpeechProperties(),new ObjectMapper());assertFalse(p.capability());assertFalse(p.healthCheck());assertThrows(IllegalStateException.class,()->p.transcribe(new byte[1]));}
 @Test void callsRealMultipartProtocolWithoutRetryOrExposingRemoteErrors()throws Exception{try(var server=new MockWebServer()){server.start();var cfg=new SpeechProperties();cfg.setEnabled(true);cfg.setApiKey("server-secret");cfg.setModel("whisper-1");cfg.setEndpoint(server.url("/v1/audio/transcriptions").toString());var p=new OpenAiCompatibleAsrProvider(cfg,new ObjectMapper());server.enqueue(new MockResponse().setBody("{\"text\":\"真实文本\"}"));assertEquals("真实文本",p.transcribe(new byte[]{1,2,3}));var req=server.takeRequest();assertEquals("Bearer server-secret",req.getHeader("Authorization"));assertEquals("/v1/audio/transcriptions",req.getPath());assertTrue(req.getBody().readUtf8().contains("filename=\"speech.wav\""));server.enqueue(new MockResponse().setResponseCode(401).setBody("server-secret"));assertFalse(assertThrows(IllegalStateException.class,()->p.transcribe(new byte[]{1})).getMessage().contains("secret"));assertEquals(2,server.getRequestCount());}}
 @Test void timeoutAlsoBoundsDelayedResponseBodyWithoutRetry()throws Exception{
  try(var server=new MockWebServer()){
   server.start();var cfg=new SpeechProperties();cfg.setEnabled(true);cfg.setApiKey("test-private-token");cfg.setModel("sensevoice-small");cfg.setTimeoutSeconds(1);cfg.setEndpoint(server.url("/v1/audio/transcriptions").toString());
   server.enqueue(new MockResponse().setBody("{\"text\":\"late transcript\"}").setBodyDelay(4,java.util.concurrent.TimeUnit.SECONDS));
   var provider=new OpenAiCompatibleAsrProvider(cfg,new ObjectMapper());
   assertTimeout(java.time.Duration.ofMillis(2500),()->assertEquals("ASR unavailable",assertThrows(IllegalStateException.class,()->provider.transcribe(new byte[]{1})).getMessage()));
   assertEquals(1,server.getRequestCount());
  }
 }

 @Test void chunkedOversizedBodyIsRejectedWithoutRetry()throws Exception{
  try(var server=new MockWebServer()){
   server.start();var cfg=new SpeechProperties();cfg.setEnabled(true);cfg.setApiKey("test-private-token");cfg.setModel("sensevoice-small");cfg.setEndpoint(server.url("/v1/audio/transcriptions").toString());
   server.enqueue(new MockResponse().setChunkedBody("x".repeat(100000),1024));
   assertEquals("ASR unavailable",assertThrows(IllegalStateException.class,()->new OpenAiCompatibleAsrProvider(cfg,new ObjectMapper()).transcribe(new byte[]{1})).getMessage());
   assertEquals(1,server.getRequestCount());
  }
 }

}
