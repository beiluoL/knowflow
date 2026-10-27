package com.knowflow.config;

import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.config.datasource.DataSourceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 测试配置必须真正支配连接，不能让宿主机外置JSON或私有模型配置接管回归环境。 */
@SpringBootTest(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:phaseg_isolation_contract;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.ai.capability.enabled=false",
        "ai.embedding-base-url=https://provider.invalid/v1",
        "knowflow.seed.development-account.enabled=false"
})
class TestIsolationContractTest {
    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties properties;
    @Autowired AiConfig ai;
    @Autowired AiCapabilityProperties capabilities;
    @Autowired Environment environment;

    @Test void actualConnectionHonorsTheTestsOwnMemoryDatabase() throws Exception {
        // 检查实际JDBC连接，而非只读Spring属性；后者无法发现DataSourceManager的外置覆盖。
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:phaseg_isolation_contract"));
        }
    }

    @Test void applicationContextCannotReadTheHostsPersistedDatasource() {
        assertFalse(Files.exists(Path.of(properties.getConfigFile())),
                "测试上下文应使用自身不存在的外置配置路径，不能读取用户当前数据库配置");
        assertEquals("h2", properties.getType());
        assertFalse(properties.isAllowRuntimeSwitch());
    }

    @Test void defaultAiIsDisabledAndCannotUseACloudEndpoint() {
        assertFalse(capabilities.isEnabled());
        assertTrue(Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(URI.create(ai.getBaseUrl()).getHost()),
                "即使私有profile含旧模型地址，普通测试默认也只能指向本地测试端点");
    }

    @Test void embeddingEndpointIsIsolatedIndependentlyOfTheChatGate() {
        // Embedding有独立配置，关闭聊天不能证明它不会调用真实供应商。
        String host = URI.create(environment.getRequiredProperty("ai.embedding-base-url")).getHost();
        assertTrue(Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host));
    }
}
