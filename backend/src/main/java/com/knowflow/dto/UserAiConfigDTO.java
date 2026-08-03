package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 保存用户 AI 配置的请求 DTO。
 * <p>编程 Agent 场景下同一用户可有多条配置，{@link #isActive} 仅用于标记通用 Chat 当前使用的配置；
 * 编程 Agent 不依赖此字段，由前端从列表中自由选择。
 */
public class UserAiConfigDTO {
    @NotBlank(message = "模型提供商不能为空")
    private String provider;
    /** 新建时必填；编辑时若为脱敏值（含 ****）则后端不覆盖。本地模型约定填 "local"。 */
    private String apiKey;
    private String baseUrl;
    private String model;
    private Integer isActive;
    /** CLOUD / LOCAL，默认 CLOUD。 */
    private String providerType;
    /** LIGHT / STANDARD / POWERFUL，默认 STANDARD。 */
    private String capability;
    /** 用户自定义显示名。 */
    private String displayName;
    /** 编辑现有配置时传入；新增不传。 */
    private Long id;
}
