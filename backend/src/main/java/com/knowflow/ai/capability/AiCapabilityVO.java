package com.knowflow.ai.capability;

import lombok.Data;

/**
 * 面向前端的 AI 能力状态视图。
 *
 * <p>安全：不包含 API Key、baseUrl、异常堆栈、供应商原始错误体；只暴露做 UI 决策所必需的信息。
 */
@Data
public class AiCapabilityVO {

    /** 系统总开关。 */
    private boolean enabled;

    /** 当前用户是否存在有效生效配置（自带 Key 或系统 Key）。 */
    private boolean configured;

    /** 七态：AVAILABLE / UNCONFIGURED / DEGRADED / RATE_LIMITED / TIMEOUT / UNAVAILABLE / DISABLED。 */
    private String status;

    /** 供应商标识（可空），仅用于展示，不包含密钥。 */
    private String provider;

    /** 近期失败原因枚举名（可空），用于针对性提示，不含内部细节。 */
    private String recentFailureKind;

    /** 是否建议前端直接走基础模式（不可立即恢复的状态）。 */
    private boolean useBasicMode;
}
