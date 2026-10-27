package com.knowflow.service;

import com.knowflow.service.impl.EmbeddingServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class EmbeddingServiceConfigurationTest {

    private EmbeddingServiceImpl service(String embeddingUrl, String embeddingKey) {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.deepseek.com/v1");
        ReflectionTestUtils.setField(service, "apiKey", "deepseek-chat-key");
        ReflectionTestUtils.setField(service, "embeddingBaseUrl", embeddingUrl);
        ReflectionTestUtils.setField(service, "embeddingApiKey", embeddingKey);
        ReflectionTestUtils.setField(service, "model", "nomic-embed-text");
        ReflectionTestUtils.setField(service, "batchSize", 16);
        return service;
    }

    @Test
    void missingEmbeddingConfigUsesKeylessLocalOllamaInsteadOfDeepSeek() {
        EmbeddingServiceImpl service = service("", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://localhost:11434/v1/embeddings"))
                .andExpect(method(POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        assertTrue(service.isAvailable(), "本地 Ollama 默认模式不应要求云端 API Key");
        assertFalse(service.embedBatch(List.of("测试")).get(0).isEmpty());
        server.verify();
    }

    @Test
    void explicitCloudEmbeddingNeverFallsBackToChatApiKey() {
        EmbeddingServiceImpl service = service("https://api.siliconflow.cn/v1", "");

        assertFalse(service.isAvailable(), "云端 embedding 必须配置独立 Key，不能复用 DeepSeek 对话 Key");
    }

    @Test
    void explicitCloudEmbeddingWithIndependentKeyIsAvailable() {
        EmbeddingServiceImpl service = service("https://api.siliconflow.cn/v1", "embedding-key");

        assertTrue(service.isAvailable());
    }

    @Test
    void missingLocalModelDisablesEmbeddingAndSuppressesLaterRequests() {
        EmbeddingServiceImpl service = service("", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://localhost:11434/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"models\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));

        service.checkConfig();

        assertFalse(service.isAvailable(), "缺少本地模型时应立即切换到关键词降级");
        assertTrue(service.embed("不会发出后续请求").isEmpty());
        server.verify();
    }

    @Test
    void deceptiveRemoteHostIsNotTreatedAsKeylessLocalOllama() {
        EmbeddingServiceImpl service = service("http://localhost:11434.evil.example/v1", "");

        assertFalse(service.isAvailable(), "只有严格匹配的本机 Ollama 地址才能免 Key");
    }

    @Test
    void runtimeLocalFailureOpensCircuitAndRecoveryProbeRestoresService() {
        EmbeddingServiceImpl service = service("", "");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://localhost:11434/v1/embeddings"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertTrue(service.embed("first").isEmpty());
        assertFalse(service.isAvailable(), "运行期失败后应打开本地熔断");
        assertTrue(service.embed("suppressed").isEmpty());
        server.verify();

        ReflectionTestUtils.setField(service, "nextLocalProbeAtNanos", 0L);
        server.reset();
        server.expect(once(), requestTo("http://localhost:11434/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"nomic-embed-text:latest\"}]}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        assertTrue(service.isAvailable(), "重试窗口到期且探测成功后应自动恢复");
        server.verify();
    }
}
