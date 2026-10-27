package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户维度词条状态：收藏与展示层学习状态。
 * user_id 必须由服务端认证上下文写入，不能信任请求体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_vocab_state")
public class UserVocabState extends BaseEntity {

    /** 用户 ID */
    private Long userId;

    /** 词条 ID */
    private Long termId;

    /** 是否收藏 */
    private Boolean favorite;

    /** NEW / LEARNING / MASTERED */
    private String learningStatus;

    /** P1 预留：关联既有闪卡 ID */
    private Long linkedFlashcardId;

    /** 最近打开时间 */
    private LocalDateTime lastOpenedAt;
}
