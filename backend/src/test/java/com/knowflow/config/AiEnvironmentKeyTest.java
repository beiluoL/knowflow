package com.knowflow.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
class AiEnvironmentKeyTest {
 private final ApplicationContextRunner context=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class)).withUserConfiguration(AiConfig.class);
 @Test void deepSeekAliasOverridesLocalFileKey(){context.withPropertyValues("ai.api-key=old-local-test-key","ai.base-url=https://api.deepseek.com/v1","DEEP_SEEK_API_KEY=env-test-key").run(c->assertEquals("env-test-key",c.getBean(AiConfig.class).getApiKey()));}
 @Test void explicitGenericEnvironmentKeyWins(){context.withPropertyValues("ai.api-key=old-local-test-key","ai.base-url=https://api.deepseek.com/v1","DEEP_SEEK_API_KEY=env-test-key","AI_API_KEY=explicit-test-key").run(c->assertEquals("explicit-test-key",c.getBean(AiConfig.class).getApiKey()));}
 @Test void deepSeekAliasDoesNotReplaceDifferentProvidersKey(){context.withPropertyValues("ai.api-key=other-test-key","ai.base-url=https://api.example.test/v1","DEEP_SEEK_API_KEY=env-test-key").run(c->assertEquals("other-test-key",c.getBean(AiConfig.class).getApiKey()));}
 @Test void conventionalDeepSeekAliasOverridesStalePrivateProfile(){context.withPropertyValues("ai.api-key=stale-private-key","ai.base-url=https://api.deepseek.com/v1","DEEPSEEK_API_KEY=conventional-test-key").run(c->assertEquals("conventional-test-key",c.getBean(AiConfig.class).getApiKey()));}
 @Test void conventionalDeepSeekAliasCannotReplaceOtherProvider(){context.withPropertyValues("ai.api-key=other-provider-key","ai.base-url=https://api.example.test/v1","DEEPSEEK_API_KEY=conventional-test-key").run(c->assertEquals("other-provider-key",c.getBean(AiConfig.class).getApiKey()));}
}
