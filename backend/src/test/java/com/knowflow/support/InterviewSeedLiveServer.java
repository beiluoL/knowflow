package com.knowflow.support;

import com.knowflow.KnowFlowApplication;
import com.knowflow.service.EmbeddingService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.*;

/** Explicit loopback fixture runner. Persistent H2 path is supplied by the verification command. */
public final class InterviewSeedLiveServer {
    @TestConfiguration public static class OfflineConfiguration {
        @Bean @Primary EmbeddingService offlineEmbedding(){return org.mockito.Mockito.mock(EmbeddingService.class);}
    }
    public static void main(String[] args) {
        List<String> options=new ArrayList<>(List.of("--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=8081","--knowflow.host-capabilities.enabled=false","--knowflow.datasource.h2.init-mode=auto","--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.slf4j.Slf4jImpl"));
        options.addAll(Arrays.asList(args));
        SpringApplication.run(new Class<?>[]{KnowFlowApplication.class,FakeAiConfiguration.class,OfflineConfiguration.class},options.toArray(String[]::new));
    }
}
