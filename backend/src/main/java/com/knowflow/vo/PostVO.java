package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区帖子视图对象，返回帖子内容、作者与互动统计数据。
 */
@Data
public class PostVO {

    private Long id;

    private Long userId;

    private String username;

    private String nickname;

    private String title;

    private String content;

    /** 帖子分类 */
    private String category;

    private String tags;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    /** 是否精华帖，0-否 1-是 */
    private Integer isEssence;

    /** 帖子状态编码 */
    private Integer status;

    private LocalDateTime createTime;
}
