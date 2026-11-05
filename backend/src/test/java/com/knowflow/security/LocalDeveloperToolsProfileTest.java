package com.knowflow.security;

import com.knowflow.config.LocalDeveloperToolsSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证本地工具须显式启用且生产配置不能开启浏览器凭据。
 */
class LocalDeveloperToolsProfileTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(LocalDeveloperToolsSecurityConfig.class);

    @Test void localNeedsExplicitOptIn() {
        for (String enabled : new String[]{"false", ""}) {
            context.withPropertyValues("spring.profiles.active=local", "knowflow.developer-tools.enabled=" + enabled)
                    .run(it -> assertThat(it).hasNotFailed().doesNotHaveBean("localDeveloperToolsChain"));
        }
    }

    @Test void productionAndMixedProfilesNeverEnableBrowserCredentials() {
        for (String profiles : new String[]{"prod", "production", "local,prod", "local,production", "test"}) {
            context.withPropertyValues("spring.profiles.active=" + profiles, "knowflow.developer-tools.enabled=true")
                    .run(it -> assertThat(it).hasNotFailed().doesNotHaveBean("localDeveloperToolsChain"));
        }
    }
}
