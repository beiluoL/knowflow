package com.knowflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.dto.CommentCreateDTO;
import com.knowflow.dto.CommentUpdateDTO;
import com.knowflow.vo.CommentLikeVO;
import com.knowflow.vo.CommentVO;

/**
 * 社区评论业务服务（F-06）。
 *
 * <p>层级策略：仅支持「一级回复」。回复的回复会被扁平化挂到同一顶级评论下，
 * 通过 replyToUserId 记录被回复者，前端以「回复 @某人」形式缩进展示，
 * 从根本上规避无限递归与深层嵌套的渲染问题。</p>
 */
public interface CommunityCommentService {

    /**
     * 分页查询某帖子的顶级评论（parentId = 0），并预加载每条评论的前若干条回复。
     *
     * @param sortBy latest（默认，最新在前）/ hot（按点赞数）/ oldest（最早在前）
     */
    IPage<CommentVO> getCommentPage(Long postId, Integer pageNum, Integer pageSize, String sortBy);

    /**
     * 分页查询某条顶级评论下的全部回复（按时间正序）。
     */
    IPage<CommentVO> getReplyPage(Long commentId, Integer pageNum, Integer pageSize);

    /**
     * 发表评论或回复。会原子更新帖子评论数与父评论回复数，并投递通知。
     */
    CommentVO addComment(CommentCreateDTO dto, Long userId);

    /**
     * 编辑评论正文，仅评论作者本人可操作。
     */
    CommentVO updateComment(Long commentId, CommentUpdateDTO dto, Long userId);

    /**
     * 删除评论（逻辑删除）。作者本人或管理员可操作；
     * 删除顶级评论时级联逻辑删除其全部子回复，并按实际删除条数回退帖子评论数。
     */
    void deleteComment(Long commentId, Long userId);

    /**
     * 点赞 / 取消点赞切换（幂等），返回切换后的状态与最新点赞数。
     */
    CommentLikeVO toggleLike(Long commentId, Long userId);

    /**
     * 帖子被删除时级联逻辑删除其全部评论，返回删除条数。
     */
    int deleteByPostId(Long postId);
}
