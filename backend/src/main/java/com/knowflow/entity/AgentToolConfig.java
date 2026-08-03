package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具启用配置：记录每个用户对各内置工具的启用状态与授权等级。
 * 逻辑外键 userId，通过索引 uk_agent_tool_config_user_tool 保证唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_tool_config")
public class AgentToolConfig extends BaseEntity {
    private Long userId;
    private String toolName;
    /** 0 禁用 / 1 启用 */
    private Integer enabled;
    /** 是否允许写操作（WRITE 工具需 true 才执行） */
    private Integer allowWrite;
}
