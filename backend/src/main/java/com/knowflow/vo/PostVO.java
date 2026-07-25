package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostVO {

    private Long id;

    private Long userId;

    private String username;

    private String nickname;

    private String title;

    private String content;

    private String category;

    private String tags;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    private Integer isEssence;

    private Integer status;

    private LocalDateTime createTime;
}
