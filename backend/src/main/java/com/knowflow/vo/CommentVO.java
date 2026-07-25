package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

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
