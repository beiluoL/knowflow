package com.knowflow.ai.capability;

import com.knowflow.ai.*;
import com.knowflow.config.*;
import com.knowflow.mapper.UserAiConfigMapper;
import com.knowflow.service.impl.AiServiceImpl;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅使用Mock Provider与本地HTTP计数；关闭门禁不能消耗付费Token。 */
class AiServiceGateTest {
 private AiCapabilityProperties props; private ModelAdapterFactory factory; private ModelAdapter adapter; private AiServiceImpl ai; private MockWebServer server; private AiProviderRegistry providers; private com.knowflow.ai.billing.AiBillingService billing;
 @BeforeEach void setup() throws Exception {
  server=new MockWebServer();server.start();props=new AiCapabilityProperties();props.setEnabled(false);
  factory=mock(ModelAdapterFactory.class);adapter=mock(ModelAdapter.class);when(factory.getAdapter(any())).thenReturn(adapter);
  var config=new AiConfig();config.setApiKey("local");config.setBaseUrl(server.url("/v1").toString());config.setModel("local-test");
  var executor=mock(AiCapabilityExecutor.class);when(executor.execute(anyString(),eq(false),any())).thenAnswer(i->((AiCapabilityExecutor.AiCall)i.getArgument(2)).invoke());
  providers=mock(AiProviderRegistry.class);
  billing=mock(com.knowflow.ai.billing.AiBillingService.class);
  ai=new AiServiceImpl(config,mock(UserAiConfigMapper.class),providers,factory,executor,mock(AiCapabilityStateService.class),props, billing);
 }
 @AfterEach void close() throws Exception {server.shutdown();}
 private void disabled(Runnable call){var ex=assertThrows(AiCapabilityException.class,call::run);assertEquals(AiFailureKind.SYSTEM_DISABLED,ex.getKind());assertFalse(ex.getMessage().contains("local-test"));verifyNoInteractions(adapter);}
 @Test void disabledSseNeverCallsProvider(){disabled(()->ai.streamChat("system","question",null,null,new SseEmitter()));}
 @Test void disabledToolChatNeverCallsProvider(){disabled(()->ai.chatMulti(List.of(),List.of(),null,null));}
 @Test void disabledToolStreamNeverCallsProvider(){disabled(()->ai.streamMulti(List.of(),List.of(),null,null,d->{},d->{},null,null,null));}
 @Test void disabledHealthCheckDoesNotProbe(){assertTrue(ai.healthCheck(null,null).contains("false"));verifyNoInteractions(adapter);}
 @Test void disabledOrdinaryAndVisionUseNoHttp(){assertNotNull(ai.chat("question",List.of()));assertNotNull(ai.chatWithImages("question",List.of("data:image/png;base64,AA=="),List.of(),null,null));assertEquals(0,server.getRequestCount());}
 @Test void enabledThenDisabledNewSseRequestStopsAtGate(){props.setEnabled(true);ai.streamChat("system","question",null,null,new SseEmitter());verify(adapter,times(1)).streamChat(any(),any(),any());props.setEnabled(false);assertThrows(AiCapabilityException.class,()->ai.streamChat("system","question",null,null,new SseEmitter()));verify(adapter,times(1)).streamChat(any(),any(),any());}
 @Test void disablingDuringAdapterResolutionBlocksInvocation(){props.setEnabled(true);when(factory.getAdapter(any())).thenAnswer(i->{props.setEnabled(false);return adapter;});ai.streamChat("system","question",null,null,new SseEmitter());verifyNoInteractions(adapter);}
 @Test void enabledOrdinaryAndVisionKeepLocalProtocol() throws Exception {props.setEnabled(true);String response="{\"choices\":[{\"message\":{\"content\":\"local provider result\"}}]}";server.enqueue(new MockResponse().setBody(response));assertEquals("local provider result",ai.chat("question",List.of()));server.enqueue(new MockResponse().setBody(response));assertEquals("local provider result",ai.chatWithImages("question",List.of("data:image/png;base64,AA=="),List.of(),null,null));assertEquals(2,server.getRequestCount());}
 @Test void configuredFallbackUsesSecondModelOnSameEndpoint() throws Exception {
  props.setEnabled(true);
  props.getFallbackModels().put("platform:local-test", "local-backup");
  server.enqueue(new MockResponse().setResponseCode(503));
  server.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"backup answer\"}}]}"));
  assertEquals("backup answer", ai.chat("question", List.of()));
  assertTrue(server.takeRequest().getBody().readUtf8().contains("local-test"));
  assertTrue(server.takeRequest().getBody().readUtf8().contains("local-backup"));
 }
 @Test void crossProviderFallbackUsesItsOwnEndpointAndCredential() throws Exception {
  props.setEnabled(true);
  var provider = new AiProviderRegistry.ProviderInfo();
  provider.setProtocol(AiProviderRegistry.Protocol.OPENAI);
  when(providers.find("backup")).thenReturn(provider);
  try (var alternate = new MockWebServer()) {
   alternate.start();
   props.getCrossProviderFallbacks().put("platform:local-test",
       new AiCapabilityProperties.Route("backup", alternate.url("/v1").toString(), "backup-model", "backup-test-key"));
   server.enqueue(new MockResponse().setResponseCode(503));
   alternate.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"alternate answer\"}}]}"));
   assertEquals("alternate answer", ai.chat("question", List.of()));
   assertEquals("Bearer local", server.takeRequest().getHeader("Authorization"));
   var fallbackRequest = alternate.takeRequest();
   assertEquals("Bearer backup-test-key", fallbackRequest.getHeader("Authorization"));
   assertTrue(fallbackRequest.getBody().readUtf8().contains("backup-model"));
  }
 }
 @Test void toolCallAndVisionSettleReportedUsage() {
  props.setEnabled(true);
  when(adapter.chat(any())).thenAnswer(call -> {
   ModelAdapter.ChatRequest request = call.getArgument(0);
   request.reportedUsage = new ModelAdapter.Usage(4L, 7L);
   return new ModelAdapter.ChatResult("tool answer", List.of());
  });
  assertEquals("tool answer", ai.chatMulti(List.of(), List.of(), null, null).content);
  verify(billing).reserve(isNull(), eq("tools"), eq("platform:local-test"));
  verify(billing).settle(isNull(), eq(4L), eq(7L), eq(true), eq(false));

  server.enqueue(new MockResponse().setBody("""
      {"choices":[{"message":{"content":"vision"}}],"usage":{"prompt_tokens":20,"completion_tokens":3}}
      """));
  assertEquals("vision", ai.chatWithImages("image", List.of("data:image/png;base64,AA=="), List.of(), null, null));
  verify(billing).reserve(isNull(), eq("vision"), anyString());
  verify(billing).settle(isNull(), eq(20L), eq(3L), eq(true), eq(false));
 }
}
