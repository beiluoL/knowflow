package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_ai_config")
/**
 * 用户级 AI 模型配置。
 * <p>
 * 同一用户可配置多条记录，通过 {@link #isActive} 标记当前在通用 Chat 中使用的那一条
 * （保持兼容：通用 Chat 仍取 isActive=1 的唯一一条）。
 * 编程 Agent 场景允许用户从全部配置中自由切换，不依赖 isActive。
 * <p>
 * 扩展字段：
 * <ul>
 *   <li>{@link #providerType}：CLOUD / LOCAL，区分云端 API 与本地推理服务</li>
 *   <li>{@link #capability}：LIGHT / STANDARD / POWERFUL，能力等级标签</li>
 *   <li>{@link #displayName}：用户自定义显示名（如 "我的本地 Llama3"），为空时回退到 provider label</li>
 * </ul>
 */
public class UserAiConfig extends BaseEntity {
    private Long userId;
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Integer isActive;

    /** CLOUD / LOCAL，默认 CLOUD，兼容旧数据。 */
    private String providerType;
    /** LIGHT / STANDARD / POWERFUL，默认 STANDARD。 */
    private String capability;
    /** 用户自定义显示名，可为空。 */
    private String displayName;
}
