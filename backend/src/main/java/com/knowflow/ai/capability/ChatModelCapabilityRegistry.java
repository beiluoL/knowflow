package com.knowflow.ai.capability;

import com.knowflow.config.AiProviderRegistry;

import java.util.Locale;

/**
 * 聊天模型能力注册表：深度思考能力的唯一解析入口。
 * <p>
 * 解析依据 provider（协议/厂商）+ 模型族，而不是散落在业务代码里的字符串判断。
 * 规则只覆盖官方明确返回推理内容的模型；未知模型一律返回不支持，避免前端出现假思考。
 * 以静态工具形式提供，便于 AiService / ChatController 复用而不改变既有构造签名。
 * </p>
 */
public final class ChatModelCapabilityRegistry {

    private ChatModelCapabilityRegistry() {}

    public static ChatModelCapability resolve(String provider, String model, boolean supportsTools) {
        ChatModelCapability capability = new ChatModelCapability();
        capability.setProvider(provider);
        capability.setModel(model);
        capability.setSupportsTools(supportsTools);
        String p = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        String m = model == null ? "" : model.toLowerCase(Locale.ROOT);

        if (m.isBlank()) {
            capability.setNote("当前未指定模型，无法启用深度思考");
            return capability;
        }

        // DeepSeek 官方：deepseek-reasoner 在流式响应的 delta 中返回 reasoning_content（原始推理文本）
        if ("deepseek".equals(p) && m.startsWith("deepseek-reasoner")) {
            return reasoningText(capability, "模型推理内容");
        }
        // 硅基流动托管的 DeepSeek-R1 系列同样返回 reasoning_content
        if ("siliconflow".equals(p) && (m.contains("deepseek-r1") || m.contains("deepseek-ai/deepseek-r1"))) {
            return reasoningText(capability, "模型推理内容");
        }
        // OpenAI 推理模型只提供 Reasoning Summary，必须通过 Responses API 获取
        if ("openai".equals(p) && isOpenAiReasoningModel(m)) {
            return summary(capability);
        }
        // 平台默认通道未声明 provider 时，按 OpenAI 兼容模型族保守判断：
        // deepseek-reasoner / R1 在 OpenAI 兼容协议下仍返回 reasoning_content。
        if ((p.isEmpty() || "platform".equals(p)) && (m.startsWith("deepseek-reasoner") || m.contains("deepseek-r1"))) {
            return reasoningText(capability, "模型推理内容");
        }

        capability.setReasoningSupported(false);
        capability.setReasoningKind("none");
        capability.setReasoningLabel("模型推理内容");
        capability.setNote("当前模型不返回可展示的推理内容，可切换支持推理的模型");
        return capability;
    }

    private static ChatModelCapability reasoningText(ChatModelCapability capability, String label) {
        capability.setReasoningSupported(true);
        capability.setReasoningKind("provider_text");
        capability.setReasoningLabel(label);
        capability.setNote(null);
        return capability;
    }

    private static ChatModelCapability summary(ChatModelCapability capability) {
        capability.setReasoningSupported(true);
        capability.setReasoningKind("summary");
        capability.setReasoningLabel("思考摘要");
        capability.setNote(null);
        return capability;
    }

    private static boolean isOpenAiReasoningModel(String model) {
        return model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4")
                || model.startsWith("gpt-5");
    }
}
