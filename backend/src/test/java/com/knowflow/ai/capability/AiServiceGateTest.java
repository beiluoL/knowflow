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
 private AiCapabilityProperties props; private ModelAdapterFactory factory; private ModelAdapter adapter; private AiServiceImpl ai; private MockWebServer server;
 @BeforeEach void setup() throws Exception {
  server=new MockWebServer();server.start();props=new AiCapabilityProperties();props.setEnabled(false);
  factory=mock(ModelAdapterFactory.class);adapter=mock(ModelAdapter.class);when(factory.getAdapter(any())).thenReturn(adapter);
  var config=new AiConfig();config.setApiKey("local");config.setBaseUrl(server.url("/v1").toString());config.setModel("local-test");
  var executor=mock(AiCapabilityExecutor.class);when(executor.execute(anyString(),eq(false),any())).thenAnswer(i->((AiCapabilityExecutor.AiCall)i.getArgument(2)).invoke());
  ai=new AiServiceImpl(config,mock(UserAiConfigMapper.class),mock(AiProviderRegistry.class),factory,executor,mock(AiCapabilityStateService.class),props);
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
}
