package com.knowflow.ai.capability;

import lombok.Data;

/**
 * 单个模型在「深度思考」场景下的真实能力描述。
 * <p>
 * 能力由 {@link ChatModelCapabilityRegistry} 依据 provider + model 解析，
 * 前端据此启用/禁用深度思考开关并展示原因，不在前端硬编码模型名单。
 * </p>
 */
@Data
public class ChatModelCapability {

    private String model;

    private String provider;

    /** 是否支持推理模式（可开启深度思考）。 */
    private boolean reasoningSupported;

    /**
     * 可展示的推理内容类型：
     * <ul>
     *   <li>provider_text：Provider 原始返回的可展示推理文本（如 DeepSeek reasoning_content）</li>
     *   <li>summary：Provider 返回的推理摘要（如 OpenAI Responses reasoning summary）</li>
     *   <li>none：无可展示推理内容</li>
     * </ul>
     */
    private String reasoningKind = "none";

    /** 面向用户的类型说明（用于面板标题，避免把摘要标成完整思维链）。 */
    private String reasoningLabel = "模型推理内容";

    /** 是否支持工具调用（与 AiProviderRegistry 的声明一致）。 */
    private boolean supportsTools;

    /** 不支持时的原因说明；支持时为空。 */
    private String note;
}
