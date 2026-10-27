package com.knowflow.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.ModelAdapterFactory;
import com.knowflow.ai.capability.*;
import com.knowflow.entity.UserAiConfig;
import com.knowflow.mapper.UserAiConfigMapper;
import com.knowflow.service.impl.AiServiceImpl;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 本地HTTP协议边界：不冒充真实LLM，只锁定显式结构化请求与普通聊天隔离。 */
class AiStructuredRequestTest {
 @Test void structuredDeepSeekDisablesThinkingAndRequestsJsonWithoutChangingOrdinaryCompletion()throws Exception{
  try(var server=new MockWebServer()){
   server.start();var mapper=mock(UserAiConfigMapper.class);var cfg=new UserAiConfig();cfg.setProvider("deepseek");cfg.setApiKey("protocol-test-secret");cfg.setModel("deepseek-flash");cfg.setBaseUrl(server.url("/v1").toString());when(mapper.selectOne(any())).thenReturn(cfg);
   var executor=mock(AiCapabilityExecutor.class);when(executor.execute(anyString(),eq(false),any())).thenAnswer(i->((AiCapabilityExecutor.AiCall)i.getArgument(2)).invoke());
   var ai=new AiServiceImpl(new AiConfig(),mapper,mock(AiProviderRegistry.class),mock(ModelAdapterFactory.class),executor,mock(AiCapabilityStateService.class),new AiCapabilityProperties());
   String response=new ObjectMapper().writeValueAsString(Map.of("choices",java.util.List.of(Map.of("message",Map.of("content","{\"ok\":true}")))));
   server.enqueue(new MockResponse().setBody(response));assertEquals("{\"ok\":true}",ai.completeStructured("Output JSON","case",null,7L));var body=new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());assertEquals("disabled",body.path("thinking").path("type").asText());assertEquals("json_object",body.path("response_format").path("type").asText());
   server.enqueue(new MockResponse().setBody(response));ai.complete("ordinary","case",(String)null,7L);body=new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());assertFalse(body.has("thinking"));assertFalse(body.has("response_format"));
  }
 }
}
