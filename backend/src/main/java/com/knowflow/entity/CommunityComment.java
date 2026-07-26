package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_comment")
/** 社区评论实体，针对某帖子的用户评论。 */
public class CommunityComment extends BaseEntity {

    private Long postId;

    private Long userId;

    private String content;
}
