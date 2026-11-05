package com.knowflow.ai.vo;

import lombok.Data;

/**
 * 承载用户 AI 配置的响应投影。
 */
@Data
public class UserAiConfigVO {
    private Long id;
    private String provider;
    /** API Key 脱敏显示（仅保留前4后4位）；本地模型为 "local"。 */
    private String apiKeyMasked;
    private String baseUrl;
    private String model;
    private Integer isActive;
    /** 部署方式：CLOUD（云端）/ LOCAL（本地）。 */
    private String providerType;
    /** 能力等级：LIGHT（轻量）/ STANDARD（标准）/ POWERFUL（高能力）。 */
    private String capability;
    /** 用户自定义显示名。 */
    private String displayName;
    /** 提供商中文标签（由 Registry 回填，便于前端直接展示）。 */
    private String providerLabel;
    /** 是否本地部署（由 Registry 回填）。 */
    private Boolean isLocal;
}
