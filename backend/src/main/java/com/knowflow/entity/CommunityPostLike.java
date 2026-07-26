package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * F-10 修复：帖子点赞关系表（用户-帖子联合唯一），支撑点赞幂等与取消点赞。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_post_like")
public class CommunityPostLike extends BaseEntity {

    private Long postId;

    private Long userId;
}
