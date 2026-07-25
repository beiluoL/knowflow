package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.entity.CommunityComment;
import com.knowflow.entity.CommunityPost;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.CommunityCommentMapper;
import com.knowflow.mapper.CommunityPostMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.CommunityService;
import com.knowflow.service.NotificationService;
import com.knowflow.vo.CommentVO;
import com.knowflow.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityServiceImpl extends ServiceImpl<CommunityPostMapper, CommunityPost> implements CommunityService {

    private final SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final CommunityCommentMapper commentMapper;

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

        Page<CommunityPost> page = this.page(new Page<>(pageNum, pageSize), wrapper);
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
        post.setUserId(userId);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setViewCount(0);
        post.setIsEssence(0);
        post.setStatus(1);
        this.save(post);
    }

    @Override
    public void likePost(Long id, Long userId) {
        CommunityPost post = this.getById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        this.update(new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, id)
                .setSql("like_count = like_count + 1"));
        if (!java.util.Objects.equals(post.getUserId(), userId)) {
            notificationService.createNotification(post.getUserId(), "like", "收到一个点赞",
                    "你的帖子《" + post.getTitle() + "》收到了一个点赞", id, "post");
        }
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
        Page<CommunityComment> page = new Page<>(pageNum, Math.min(pageSize, 100));
        LambdaQueryWrapper<CommunityComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityComment::getPostId, postId)
                .orderByAsc(CommunityComment::getCreateTime);
        Page<CommunityComment> result = commentMapper.selectPage(page, wrapper);
        return result.convert(this::convertCommentToVO);
    }

    @Override
    @Transactional
    public void addComment(CommunityComment comment, Long userId) {
        CommunityPost post = this.getById(comment.getPostId());
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
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
