package com.knowflow.config;

import lombok.Data;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型提供商统一注册表。
 * <p>
 * 作为项目内 11 个云端提供商 + 4 类本地推理服务的唯一权威来源，
 * 供 {@link com.knowflow.controller.AiConfigController#platformModels} 与
 * 编程 Agent（{@link com.knowflow.controller.CodeAgentController}）共用，
 * 消除此前「前端 Chat.vue 11 个、后端 4 个」的不一致。
 * <p>
 * 提供商分类：
 * <ul>
 *   <li>{@link ProviderType#CLOUD}：云端 API，需要 API Key</li>
 *   <li>{@link ProviderType#LOCAL}：本地推理服务（Ollama/vLLM/LocalAI/自定义），
 *       走 OpenAI 兼容接口，无需 API Key（约定填 "local" 占位）</li>
 * </ul>
 * 能力等级（{@link Capability}）用于编程 Agent 场景的任务路由建议：
 * <ul>
 *   <li>LIGHT：轻量模型，适合简单代码补全/解释</li>
 *   <li>STANDARD：标准模型，适合常规编程问答</li>
 *   <li>POWERFUL：强力模型，适合复杂推理/重构</li>
 * </ul>
 */
@Component
public class AiProviderRegistry {

    /** 云端 + 本地全部内置提供商（按推荐顺序排列）。 */
    private final List<ProviderInfo> builtins = new ArrayList<>();
    /** provider id → ProviderInfo 的查找索引。 */
    private final Map<String, ProviderInfo> index = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        // ===== 云端 11 个（与 Chat.vue 原 aiProviders 保持一致）=====
        // protocol：anthropic/wenxin 走原生协议，其余 OpenAI 兼容
        register("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.OPENAI, false, "免费体验额度，超出需订阅");
        register("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct",
                ProviderType.CLOUD, Capability.LIGHT, Protocol.OPENAI, false, "免费额度，超出需订阅");
        register("bailian", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.OPENAI, false, "免费额度，超出需订阅");
        register("zhipu", "智谱AI", "https://open.bigmodel.cn/api/paas/v4", "glm-4",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.OPENAI, false, "免费额度，超出需订阅");
        register("moonshot", "月之暗面", "https://api.moonshot.cn/v1", "moonshot-v1-8k",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.OPENAI, false, "需付费");
        register("doubao", "字节豆包", "https://ark.cn-beijing.volces.com/api/v3", "doubao-pro-32k",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.OPENAI, false, "需付费");
        register("hunyuan", "腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-pro",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.OPENAI, false, "需付费");
        register("wenxin", "百度文心", "https://qianfan.baidubce.com/v2", "ernie-4.0-8k",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.QIANFAN, false, "需付费");
        register("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o",
                ProviderType.CLOUD, Capability.POWERFUL, Protocol.OPENAI, true, "需要订阅，按量计费");
        register("anthropic", "Anthropic", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022",
                ProviderType.CLOUD, Capability.POWERFUL, Protocol.ANTHROPIC, true, "需要订阅，按量计费");
        register("custom", "自定义（云端）", "", "",
                ProviderType.CLOUD, Capability.STANDARD, Protocol.OPENAI, false, "用户自定义接口地址");

        // ===== 本地 4 类（OpenAI 兼容协议）=====
        register("ollama", "Ollama（本地）", "http://localhost:11434/v1", "llama3.1",
                ProviderType.LOCAL, Capability.LIGHT, Protocol.OPENAI, false, "本地部署，免费", false);
        register("vllm", "vLLM（本地）", "http://localhost:8000/v1", "Qwen/Qwen2.5-7B-Instruct",
                ProviderType.LOCAL, Capability.STANDARD, Protocol.OPENAI, false, "本地部署，免费", false);
        register("localai", "LocalAI（本地）", "http://localhost:8080/v1", "gpt-3.5-turbo",
                ProviderType.LOCAL, Capability.LIGHT, Protocol.OPENAI, false, "本地部署，免费", false);
        register("custom-local", "自定义（本地）", "", "",
                ProviderType.LOCAL, Capability.STANDARD, Protocol.OPENAI, false, "用户自定义本地接口地址", false);
    }

    private void register(String id, String label, String baseUrl, String defaultModel,
                          ProviderType type, Capability capability, Protocol protocol,
                          boolean subscriptionRequired, String priceInfo) {
        register(id, label, baseUrl, defaultModel, type, capability, protocol,
                subscriptionRequired, priceInfo, true);
    }

    private void register(String id, String label, String baseUrl, String defaultModel,
                          ProviderType type, Capability capability, Protocol protocol,
                          boolean subscriptionRequired, String priceInfo, boolean supportsTools) {
        ProviderInfo info = new ProviderInfo();
        info.setProvider(id);
        info.setLabel(label);
        info.setBaseUrl(baseUrl);
        info.setDefaultModel(defaultModel);
        info.setType(type);
        info.setCapability(capability);
        info.setProtocol(protocol);
        info.setSubscriptionRequired(subscriptionRequired);
        info.setPriceInfo(priceInfo);
        info.setSupportsTools(supportsTools);
        builtins.add(info);
        index.put(id, info);
    }

    /** 全部内置提供商列表（不可变副本）。 */
    public List<ProviderInfo> all() {
        return Collections.unmodifiableList(builtins);
    }

    /** 仅本地提供商。 */
    public List<ProviderInfo> locals() {
        return builtins.stream().filter(p -> p.getType() == ProviderType.LOCAL).toList();
    }

    /** 仅云端提供商。 */
    public List<ProviderInfo> clouds() {
        return builtins.stream().filter(p -> p.getType() == ProviderType.CLOUD).toList();
    }

    /** 按 id 查找；不存在返回 null。 */
    public ProviderInfo find(String providerId) {
        if (providerId == null) return null;
        return index.get(providerId);
    }

    /** 获取提供商默认 baseUrl；未知提供商返回 null。 */
    public String defaultBaseUrl(String providerId) {
        ProviderInfo info = find(providerId);
        return info == null ? null : info.getBaseUrl();
    }

    /** 获取提供商默认模型；未知返回 null。 */
    public String defaultModel(String providerId) {
        ProviderInfo info = find(providerId);
        return info == null ? null : info.getDefaultModel();
    }

    /** 是否为本地提供商。 */
    public boolean isLocal(String providerId) {
        ProviderInfo info = find(providerId);
        return info != null && info.getType() == ProviderType.LOCAL;
    }

    public enum ProviderType { CLOUD, LOCAL }

    public enum Capability { LIGHT, STANDARD, POWERFUL }

    /** 模型对接协议：决定底层使用哪种 API 适配实现。 */
    public enum Protocol {
        /** OpenAI / DeepSeek / 通义 / Ollama 等兼容 /v1/chat/completions 的接口 */
        OPENAI,
        /** Anthropic Claude，使用 /v1/messages + x-api-key 鉴权 */
        ANTHROPIC,
        /** 百度文心一言，使用千帆 qianfan 鉴权 + functions 工具调用 */
        QIANFAN
    }

    @Data
    public static class ProviderInfo {
        private String provider;
        private String label;
        private String baseUrl;
        private String defaultModel;
        private ProviderType type;
        private Capability capability;
        private Protocol protocol;
        private boolean subscriptionRequired;
        private String priceInfo;
        /**
         * 是否支持 Function Calling / tools 参数。
         * 本地推理服务默认 false（具体模型是否支持取决于模型本身，如 deepseek-coder:6.7b 不支持，
         * 直接下发 tools 会触发 400 Bad Request），云端 OpenAI 兼容厂商默认 true。
         */
        private boolean supportsTools = true;
    }

    /**
     * 判断指定 provider 是否支持 tools（Function Calling）。
     * 未知 provider 默认按云端兼容处理（支持 tools）。
     */
    public boolean supportsTools(String provider) {
        if (provider == null) {
            return true;
        }
        ProviderInfo info = index.get(provider.toLowerCase());
        return info == null || info.isSupportsTools();
    }
}
