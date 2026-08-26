package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_conversation")
/** 对话会话实体，聚合某用户的多轮聊天消息。 */
public class ChatConversation extends BaseEntity {

    private Long userId;

    private String title;

    private Integer messageCount;

    private String lastMessage;

    /** F1：对话摘要，用于长对话上下文压缩。每 N 轮由 AI 生成一次。 */
    private String summary;

    /** F1：摘要最后更新时间。 */
    private LocalDateTime summaryUpdatedAt;
}
