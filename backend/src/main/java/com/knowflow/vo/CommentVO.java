package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论视图对象，返回评论内容、作者与发布时间。
 */
@Data
public class CommentVO {

    private Long id;

    private Long postId;

    private Long userId;

    private String content;

    private String username;

    private String nickname;

    private String avatar;

    private LocalDateTime createTime;
}
