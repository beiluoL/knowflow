package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型调用日志实体。
 * <p>
 * 记录每次模型调用的成功/失败、耗时、token 用量等，用于模型监测仪表盘统计：
 * <ul>
 *   <li>响应时间（avg / p95）</li>
 *   <li>调用次数（按时间/按模型）</li>
 *   <li>错误率</li>
 * </ul>
 * 该表不使用逻辑删除（deleted 字段），直接物理保留作为历史日志。
 */
@Data
@TableName("agent_call_log")
public class AgentCallLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long configId;
    /** 提供商（冗余，便于按提供商聚合） */
    private String provider;
    private Long sessionId;

    /** 1 成功 / 0 失败 */
    private Integer success;
    /** 响应耗时（毫秒） */
    private Integer latencyMs;
    private Integer tokenIn;
    private Integer tokenOut;
    private String errorMsg;
    /** 输出准确率评估得分 0~1（P3 评估闭环写入，可空） */
    private java.math.BigDecimal score;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
}
