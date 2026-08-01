package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 学习建议缓存实体。
 * 按用户 + 周期维度缓存 AI 生成的学习建议，避免重复调用大模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_learning_suggestion")
public class AiLearningSuggestion extends BaseEntity {
    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;
    /** JSON 数组字符串：[{title,desc,icon,path}] */
    private String suggestions;
    /** 周期：week/month */
    private String period;
}
