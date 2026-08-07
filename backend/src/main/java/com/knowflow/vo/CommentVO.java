package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图对象，返回评论内容、作者、互动计数与当前用户的操作权限。
 */
@Data
public class CommentVO {

    private Long id;

    private Long postId;

    private Long userId;

    /** 0 = 顶级评论，非 0 = 所属顶级评论 ID */
    private Long parentId;

    /** 被回复用户 ID，0 表示直接回复顶级评论 */
    private Long replyToUserId;

    /** 被回复用户昵称，供前端渲染「回复 @某人」 */
    private String replyToNickname;

    private String content;

    private String username;

    private String nickname;

    private String avatar;

    private Integer likeCount;

    private Integer replyCount;

    /** 当前请求用户是否已点赞该评论（未登录恒为 false） */
    private Boolean liked;

    /** 当前请求用户是否为评论作者 */
    private Boolean author;

    /** 当前请求用户是否可编辑（仅作者） */
    private Boolean canEdit;

    /** 当前请求用户是否可删除（作者或管理员） */
    private Boolean canDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 顶级评论预加载的前若干条回复，便于前端首屏直接展示 */
    private List<CommentVO> replies;
}
