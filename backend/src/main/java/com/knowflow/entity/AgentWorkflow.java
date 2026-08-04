package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义工作流配置：用户预设 prompt 模板 + 触发条件，由 Agent 编排层在对话入口注入。
 * 逻辑外键 userId，通过索引 idx_agent_workflow_user 加速查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow")
public class AgentWorkflow extends BaseEntity {
    private Long userId;
    /** 工作流名称 */
    private String name;
    /** 触发类型：intent(按意图) / keyword(关键词) / manual(手动) */
    private String triggerType;
    /** 触发值：意图类型(generate/review...) 或触发关键词，逗号分隔；manual 时可空 */
    private String triggerValue;
    /** prompt 模板，支持占位符 {input} / {file} / {tree} */
    private String promptTemplate;
    /** 0 禁用 / 1 启用 */
    private Integer enabled;
    /** 排序，越小越优先（命中多个时取首个） */
    private Integer sortOrder;
}
