package com.knowflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.dto.CommentCreateDTO;
import com.knowflow.entity.CommunityPost;
import com.knowflow.service.CommunityCommentService;
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
    private final CommunityCommentService commentService;

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

    /**
     * 兼容端点：等价于 GET /api/community/comments/post/{id}，保留以免旧客户端失效。
     */
    @Operation(summary = "评论列表（兼容端点）")
    @GetMapping("/posts/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "latest") String sortBy) {
        IPage<CommentVO> page = commentService.getCommentPage(id, pageNum, pageSize, sortBy);
        return Result.success(PageResult.of(page));
    }

    /**
     * 兼容端点：等价于 POST /api/community/comments，帖子 ID 取自路径。
     */
    @Operation(summary = "发表评论（兼容端点）")
    @PostMapping("/posts/{id}/comments")
    public Result<CommentVO> addComment(@PathVariable Long id, @RequestBody CommentCreateDTO dto,
                                        Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        dto.setPostId(id);
        return Result.success(commentService.addComment(dto, userId));
    }
}
