package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_post")
public class CommunityPost extends BaseEntity {

    private Long userId;

    private String title;

    private String content;

    private String category;

    private String tags;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    private Integer isEssence;

    private Integer status;
}
