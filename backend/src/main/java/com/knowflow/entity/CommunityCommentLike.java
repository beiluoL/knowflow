package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * F-06：评论点赞关系表（用户-评论联合唯一），支撑评论点赞幂等与取消点赞。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_comment_like")
public class CommunityCommentLike extends BaseEntity {

    private Long commentId;

    private Long userId;
}
