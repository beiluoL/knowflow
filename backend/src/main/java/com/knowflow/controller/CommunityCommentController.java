package com.knowflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.CommentCreateDTO;
import com.knowflow.dto.CommentUpdateDTO;
import com.knowflow.service.CommunityCommentService;
import com.knowflow.vo.CommentLikeVO;
import com.knowflow.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区评论接口（F-06）。
 *
 * <p>读接口公开可访问（匿名可看评论，但不带点赞/权限标记）；
 * 写接口需登录，具体归属校验在 Service 层完成。</p>
 */
@Tag(name = "社区评论接口")
@RestController
@RequestMapping("/api/community/comments")
@RequiredArgsConstructor
public class CommunityCommentController {

    private final CommunityCommentService commentService;

    @Operation(summary = "帖子评论列表（顶级评论 + 预加载回复）")
    @GetMapping("/post/{postId}")
    public Result<PageResult<CommentVO>> listByPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "latest") String sortBy) {
        IPage<CommentVO> page = commentService.getCommentPage(postId, pageNum, pageSize, sortBy);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "某条评论的回复列表")
    @GetMapping("/{commentId}/replies")
    public Result<PageResult<CommentVO>> replies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        IPage<CommentVO> page = commentService.getReplyPage(commentId, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "发表评论 / 回复")
    @PostMapping
    public Result<CommentVO> create(@Valid @RequestBody CommentCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(commentService.addComment(dto, userId));
    }

    @Operation(summary = "编辑自己的评论")
    @PutMapping("/{id}")
    public Result<CommentVO> update(@PathVariable Long id, @Valid @RequestBody CommentUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(commentService.updateComment(id, dto, userId));
    }

    @Operation(summary = "删除评论（作者本人或管理员）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        commentService.deleteComment(id, userId);
        return Result.success();
    }

    @Operation(summary = "评论点赞 / 取消点赞（幂等切换）")
    @PostMapping("/{id}/like")
    public Result<CommentLikeVO> toggleLike(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(commentService.toggleLike(id, userId));
    }
}
