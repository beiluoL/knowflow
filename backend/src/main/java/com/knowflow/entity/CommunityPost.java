package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_post")
/** 社区帖子实体，用户发布的内容，含互动统计与审核状态。 */
public class CommunityPost extends BaseEntity {

    private Long userId;

    private String title;

    private String content;

    private String category;

    private String tags;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    /** 是否精华帖，0 否 / 1 是。 */
    private Integer isEssence;

    /** 帖子状态，如 0 待审核 / 1 已发布 / 2 下架。 */
    private Integer status;
}
