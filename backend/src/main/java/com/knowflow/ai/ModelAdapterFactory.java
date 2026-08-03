package com.knowflow.ai;

import com.knowflow.config.AiProviderRegistry;
import com.knowflow.config.AiProviderRegistry.Protocol;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模型适配器工厂：依据 provider 在 {@link AiProviderRegistry} 中声明的 protocol，
 * 选择对应的 {@link ModelAdapter} 实现（OpenAI/Anthropic/Qianfan）。
 */
@Component
public class ModelAdapterFactory {

    private final AiProviderRegistry registry;
    private final ObjectMapper objectMapper;

    public ModelAdapterFactory(AiProviderRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public ModelAdapter getAdapter(String provider) {
        AiProviderRegistry.ProviderInfo info = registry.find(provider);
        Protocol protocol = info != null ? info.getProtocol() : Protocol.OPENAI;
        return switch (protocol) {
            case ANTHROPIC -> new AnthropicAdapter(objectMapper);
            case QIANFAN -> new QianfanAdapter(objectMapper);
            default -> new OpenAiAdapter(objectMapper);
        };
    }
}
