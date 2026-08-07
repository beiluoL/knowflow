package com.knowflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论点赞切换结果：返回切换后的状态与最新点赞数，供前端直接同步 UI。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentLikeVO {

    /** 切换后当前用户是否处于已点赞状态 */
    private Boolean liked;

    /** 切换后的最新点赞数 */
    private Integer likeCount;
}
