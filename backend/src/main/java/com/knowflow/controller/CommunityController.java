package com.knowflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.entity.CommunityComment;
import com.knowflow.entity.CommunityPost;
import com.knowflow.service.CommunityService;
import com.knowflow.vo.CommentVO;
import com.knowflow.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "社区讨论接口")
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @Operation(summary = "帖子列表")
    @GetMapping("/posts")
    public Result<PageResult<PostVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<PostVO> page = communityService.getPostPage(category, sort, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/posts/{id}")
    public Result<PostVO> detail(@PathVariable Long id) {
        return Result.success(communityService.getPostDetail(id));
    }

    @Operation(summary = "发布帖子")
    @PostMapping("/posts")
    public Result<Void> create(@RequestBody CommunityPost post, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        communityService.createPost(post, userId);
        return Result.success();
    }

    @Operation(summary = "删除帖子")
    @DeleteMapping("/posts/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        communityService.deletePost(id, userId);
        return Result.success();
    }

    @Operation(summary = "点赞/取消点赞（幂等切换，返回当前是否已赞）")
    @PostMapping("/posts/{id}/like")
    public Result<Boolean> like(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(communityService.likePost(id, userId));
    }

    @Operation(summary = "评论列表")
    @GetMapping("/posts/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        IPage<CommentVO> page = communityService.getCommentPage(id, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "发表评论")
    @PostMapping("/posts/{id}/comments")
    public Result<Void> addComment(@PathVariable Long id, @RequestBody CommunityComment comment, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        comment.setPostId(id);
        communityService.addComment(comment, userId);
        return Result.success();
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        communityService.deleteComment(id, userId);
        return Result.success();
    }
}
