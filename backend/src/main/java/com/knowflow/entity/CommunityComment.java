package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_comment")
/** 社区评论实体，针对某帖子的用户评论，支持一级回复（parentId 指向顶级评论）。 */
public class CommunityComment extends BaseEntity {

    private Long postId;

    private Long userId;

    /** 0 = 顶级评论；非 0 = 该回复所属的顶级评论 ID（仅一级，回复的回复仍挂顶级） */
    private Long parentId;

    /** 被回复用户 ID，0 表示直接回复顶级评论。用于 UI 展示「回复 @某人」 */
    private Long replyToUserId;

    private String content;

    /** 点赞数，由 community_comment_like 关系表驱动，原子增减 */
    private Integer likeCount;

    /** 回复数，仅 parentId=0 的顶级评论维护 */
    private Integer replyCount;
}
