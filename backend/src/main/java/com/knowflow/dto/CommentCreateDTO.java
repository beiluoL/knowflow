package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论请求（F-06）。
 *
 * <p>parentId 为 0 表示顶级评论；非 0 表示回复。为避免无限层级，
 * 服务端会将「回复的回复」统一扁平化挂到顶级评论下（只保留一级），
 * 被回复者通过 replyToUserId 记录，前端展示为「回复 @某人」。</p>
 */
@Data
public class CommentCreateDTO {

    /** 所属帖子 ID；走 /posts/{id}/comments 兼容端点时由路径变量回填 */
    private Long postId;

    /** 父评论 ID，0 或 null 表示顶级评论 */
    private Long parentId;

    /** 被回复的评论 ID（回复某条子回复时传入，用于解析 replyToUserId） */
    private Long replyToCommentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过 1000 字")
    private String content;
}
