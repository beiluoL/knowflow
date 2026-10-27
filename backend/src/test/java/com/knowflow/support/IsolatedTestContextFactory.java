package com.knowflow.support;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 仅在测试classpath注册：隔离宿主机外置数据库、默认模型地址和文件目录。
 * 生产DataSourceManager的外置配置优先级保持原合同，不为测试修改运行时行为。
 */
public final class IsolatedTestContextFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> attributes) {
        // 类身份参与缓存键；不同测试类不能意外复用有可变Fake状态的Spring上下文。
        return new Isolation(testClass.getName());
    }

    private record Isolation(String testClassName) implements ContextCustomizer {
        @Override
        public void customizeContext(ConfigurableApplicationContext context,
                                     MergedContextConfiguration configuration) {
            try {
                var directory = Files.createTempDirectory("knowflow-test-context-");
                var environment = context.getEnvironment();
                String url = environment.getProperty("knowflow.datasource.h2.url", "jdbc:h2:mem:knowflow_test");
                if (!url.startsWith("jdbc:h2:mem:")) url = "jdbc:h2:mem:knowflow_test";
                int separator = url.indexOf(';');
                String name = separator < 0 ? url : url.substring(0, separator);
                String options = separator < 0 ? ";DB_CLOSE_DELAY=-1;MODE=MySQL" : url.substring(separator);
                Map<String, Object> isolated = new LinkedHashMap<>();
                isolated.put("knowflow.datasource.type", "h2");
                isolated.put("knowflow.datasource.h2.url", name + "_" + UUID.randomUUID().toString().replace("-", "") + options);
                // 保留测试声明的库名前缀，试点数据库白名单仍由原业务守卫判断。
                isolated.put("knowflow.datasource.config-file", directory.resolve("no-host-datasource.json").toString());
                isolated.put("knowflow.datasource.allow-runtime-switch", "false");
                isolated.put("app.upload.dir", directory.resolve("uploads").toString());
                isolated.put("code.execution.workspace-root", directory.resolve("workspaces").toString());
                isolated.put("AI_API_KEY", "");
                isolated.put("DEEP_SEEK_API_KEY", "");
                isolated.put("DEEPSEEK_API_KEY", "");
                // 嵌入调用不受聊天能力开关支配，测试也不得继承真实Ollama/云端嵌入配置。
                isolated.put("ai.embedding-base-url", "http://127.0.0.1:1/v1");
                isolated.put("ai.embedding-api-key", "");
                // 普通测试即使继承私有local profile，也不能连接其中的云端模型。
                // 协议测试显式声明的回环MockWebServer地址继续生效。
                if (!loopback(environment.getProperty("ai.base-url"))) {
                    isolated.put("ai.base-url", "http://127.0.0.1:1/v1");
                    isolated.put("ai.api-key", "test-only-no-provider");
                }
                context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("knowflow-test-isolation", isolated));
            } catch (IOException error) {
                throw new IllegalStateException("无法创建私有测试环境，拒绝回退到用户工作配置", error);
            }
        }

        private static boolean loopback(String address) {
            if (address == null) return false;
            try {
                String host = URI.create(address).getHost();
                return host != null && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host);
            } catch (IllegalArgumentException error) {
                return false;
            }
        }
    }
}
