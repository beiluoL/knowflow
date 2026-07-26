package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.common.PageQuery;
import com.knowflow.entity.CommunityComment;
import com.knowflow.entity.CommunityPost;
import com.knowflow.entity.CommunityPostLike;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.CommunityCommentMapper;
import com.knowflow.mapper.CommunityPostLikeMapper;
import com.knowflow.mapper.CommunityPostMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.CommunityService;
import com.knowflow.service.NotificationService;
import com.knowflow.vo.CommentVO;
import com.knowflow.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 社区（帖子 / 评论 / 点赞）业务服务实现。 */
@Service
@RequiredArgsConstructor
public class CommunityServiceImpl extends ServiceImpl<CommunityPostMapper, CommunityPost> implements CommunityService {

    private final SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final CommunityCommentMapper commentMapper;
    private final CommunityPostLikeMapper postLikeMapper;

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final int MAX_COMMENT_LENGTH = 1000;

    @Override
    public IPage<PostVO> getPostPage(String category, String sort, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityPost::getStatus, 1);

        if (category != null && !category.isEmpty()) {
            wrapper.eq(CommunityPost::getCategory, category);
        }

        if ("hot".equals(sort)) {
            wrapper.orderByDesc(CommunityPost::getViewCount);
            wrapper.orderByDesc(CommunityPost::getLikeCount);
        } else if ("essence".equals(sort)) {
            wrapper.eq(CommunityPost::getIsEssence, 1);
            wrapper.orderByDesc(CommunityPost::getCreateTime);
        } else {
            wrapper.orderByDesc(CommunityPost::getCreateTime);
        }

        // F-09/F-13 修复：分页参数统一归一化（pageSize<=100、pageNum>=1）
        Page<CommunityPost> page = this.page(
                new Page<>(PageQuery.normalizePageNum(pageNum), PageQuery.normalizePageSize(pageSize)), wrapper);
        return page.convert(this::convertToVO);
    }

    @Override
    public PostVO getPostDetail(Long id) {
        CommunityPost post = this.getById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        this.update(new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, id)
                .setSql("view_count = view_count + 1"));
        post.setViewCount(post.getViewCount() + 1);
        return convertToVO(post);
    }

    @Override
    public void createPost(CommunityPost post, Long userId) {
        // F-05 同源修复：发帖内容校验
        if (post.getTitle() == null || post.getTitle().trim().isEmpty()) {
            throw new BusinessException(400, "帖子标题不能为空");
        }
        if (post.getTitle().length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(400, "帖子标题不能超过 " + MAX_TITLE_LENGTH + " 字");
        }
        if (post.getContent() != null && post.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(400, "帖子内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        post.setTitle(post.getTitle().trim());
        post.setUserId(userId);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setViewCount(0);
        post.setIsEssence(0);
        post.setStatus(1);
        this.save(post);
    }

    @Override
    @Transactional
    public boolean likePost(Long id, Long userId) {
        CommunityPost post = this.getById(id);
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        // F-10 修复：基于点赞关系表实现幂等切换（已赞→取消，未赞→点赞）
        CommunityPostLike existLike = postLikeMapper.selectOne(new LambdaQueryWrapper<CommunityPostLike>()
                .eq(CommunityPostLike::getPostId, id)
                .eq(CommunityPostLike::getUserId, userId));
        if (existLike != null) {
            postLikeMapper.deleteById(existLike.getId());
            this.update(new LambdaUpdateWrapper<CommunityPost>()
                    .eq(CommunityPost::getId, id)
                    .setSql("like_count = GREATEST(0, like_count - 1)"));
            return false;
        }
        CommunityPostLike like = new CommunityPostLike();
        like.setPostId(id);
        like.setUserId(userId);
        postLikeMapper.insert(like);
        this.update(new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, id)
                .setSql("like_count = like_count + 1"));
        if (!java.util.Objects.equals(post.getUserId(), userId)) {
            notificationService.createNotification(post.getUserId(), "like", "收到一个点赞",
                    "你的帖子《" + post.getTitle() + "》收到了一个点赞", id, "post");
        }
        return true;
    }

    @Override
    @Transactional
    public void deletePost(Long id, Long userId) {
        CommunityPost post = this.getById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!java.util.Objects.equals(post.getUserId(), userId)) {
            throw new BusinessException("无权删除他人帖子");
        }
        commentMapper.delete(new LambdaQueryWrapper<CommunityComment>()
                .eq(CommunityComment::getPostId, id));
        this.removeById(id);
    }

    @Override
    public IPage<CommentVO> getCommentPage(Long postId, Integer pageNum, Integer pageSize) {
        Page<CommunityComment> page = new Page<>(
                PageQuery.normalizePageNum(pageNum), PageQuery.normalizePageSize(pageSize));
        LambdaQueryWrapper<CommunityComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityComment::getPostId, postId)
                .orderByAsc(CommunityComment::getCreateTime);
        Page<CommunityComment> result = commentMapper.selectPage(page, wrapper);
        return result.convert(this::convertCommentToVO);
    }

    @Override
    @Transactional
    public void addComment(CommunityComment comment, Long userId) {
        // F-05 修复：评论空值与长度校验
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (comment.getContent().length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(400, "评论内容不能超过 " + MAX_COMMENT_LENGTH + " 字");
        }
        CommunityPost post = this.getById(comment.getPostId());
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        comment.setContent(comment.getContent().trim());
        comment.setUserId(userId);
        commentMapper.insert(comment);
        this.update(new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, comment.getPostId())
                .setSql("comment_count = comment_count + 1"));
        if (!java.util.Objects.equals(post.getUserId(), userId)) {
            notificationService.createNotification(post.getUserId(), "comment", "收到一条评论",
                    "你的帖子《" + post.getTitle() + "》收到了新评论", comment.getPostId(), "post");
        }
    }

    @Override
    @Transactional
    public void deleteComment(Long id, Long userId) {
        CommunityComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!java.util.Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException("无权删除他人评论");
        }
        commentMapper.deleteById(id);
        this.update(new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, comment.getPostId())
                .setSql("comment_count = comment_count - 1"));
    }

    private CommentVO convertCommentToVO(CommunityComment comment) {
        CommentVO vo = BeanUtil.copyProperties(comment, CommentVO.class);
        SysUser user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        return vo;
    }

    private PostVO convertToVO(CommunityPost post) {
        PostVO vo = BeanUtil.copyProperties(post, PostVO.class);
        SysUser user = userMapper.selectById(post.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }
        return vo;
    }
}
