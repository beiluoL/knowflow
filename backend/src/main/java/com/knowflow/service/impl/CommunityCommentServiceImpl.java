package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.PageQuery;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.CommentCreateDTO;
import com.knowflow.dto.CommentUpdateDTO;
import com.knowflow.entity.CommunityComment;
import com.knowflow.entity.CommunityCommentLike;
import com.knowflow.entity.CommunityPost;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.CommunityCommentLikeMapper;
import com.knowflow.mapper.CommunityCommentMapper;
import com.knowflow.mapper.CommunityPostMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.CommunityCommentService;
import com.knowflow.service.NotificationService;
import com.knowflow.vo.CommentLikeVO;
import com.knowflow.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社区评论业务实现（F-06）。
 *
 * <p>三条硬性约束：
 * <ol>
 *   <li>计数器（帖子 comment_count、评论 like_count/reply_count）一律走原子 SQL 自增自减，
 *       杜绝「先查后写」的并发丢失（P1-M10）；</li>
 *   <li>编辑 / 删除前必须做归属校验，作者本人或（删除场景下的）管理员才放行（P1-M22）；</li>
 *   <li>所有删除均为逻辑删除，且写操作在同一事务内完成。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CommunityCommentServiceImpl implements CommunityCommentService {

    private final CommunityCommentMapper commentMapper;
    private final CommunityCommentLikeMapper commentLikeMapper;
    private final CommunityPostMapper postMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;

    /** 评论正文长度上限 */
    private static final int MAX_COMMENT_LENGTH = 1000;
    /** 顶级评论列表中每条预加载的回复条数 */
    private static final int PRELOAD_REPLY_SIZE = 3;
    /** 单次预加载回复的总量上限，避免热门评论把整页请求拖垮 */
    private static final int PRELOAD_REPLY_LIMIT = 200;

    // ==================== 查询 ====================

    @Override
    public IPage<CommentVO> getCommentPage(Long postId, Integer pageNum, Integer pageSize, String sortBy) {
        LambdaQueryWrapper<CommunityComment> wrapper = new LambdaQueryWrapper<CommunityComment>()
                .eq(CommunityComment::getPostId, postId)
                .eq(CommunityComment::getParentId, 0L);
        applySort(wrapper, sortBy);

        Page<CommunityComment> page = commentMapper.selectPage(
                new Page<>(PageQuery.normalizePageNum(pageNum), PageQuery.normalizePageSize(pageSize)), wrapper);

        List<CommunityComment> records = page.getRecords();
        Long currentUserId = SecurityUtils.getCurrentUserIdNullable();

        // 预加载回复：一次性捞出本页顶级评论的回复，Java 侧分组后每条取前 N 条
        Map<Long, List<CommunityComment>> replyMap = loadReplies(records);
        List<CommunityComment> all = new ArrayList<>(records);
        replyMap.values().forEach(all::addAll);

        Map<Long, SysUser> userMap = loadUsers(all);
        Set<Long> likedIds = loadLikedCommentIds(all, currentUserId);

        Page<CommentVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records.stream().map(c -> {
            CommentVO vo = toVO(c, userMap, likedIds, currentUserId);
            List<CommunityComment> replies = replyMap.get(c.getId());
            if (replies != null && !replies.isEmpty()) {
                vo.setReplies(replies.stream()
                        .limit(PRELOAD_REPLY_SIZE)
                        .map(r -> toVO(r, userMap, likedIds, currentUserId))
                        .collect(Collectors.toList()));
            } else {
                vo.setReplies(Collections.emptyList());
            }
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public IPage<CommentVO> getReplyPage(Long commentId, Integer pageNum, Integer pageSize) {
        CommunityComment parent = commentMapper.selectById(commentId);
        if (parent == null) {
            throw new BusinessException(404, "评论不存在");
        }
        Page<CommunityComment> page = commentMapper.selectPage(
                new Page<>(PageQuery.normalizePageNum(pageNum), PageQuery.normalizePageSize(pageSize)),
                new LambdaQueryWrapper<CommunityComment>()
                        .eq(CommunityComment::getParentId, commentId)
                        .orderByAsc(CommunityComment::getCreateTime));

        Long currentUserId = SecurityUtils.getCurrentUserIdNullable();
        Map<Long, SysUser> userMap = loadUsers(page.getRecords());
        Set<Long> likedIds = loadLikedCommentIds(page.getRecords(), currentUserId);

        Page<CommentVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(c -> toVO(c, userMap, likedIds, currentUserId))
                .collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO addComment(CommentCreateDTO dto, Long userId) {
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(400, "评论内容不能超过 " + MAX_COMMENT_LENGTH + " 字");
        }
        if (dto.getPostId() == null) {
            throw new BusinessException(400, "缺少帖子 ID");
        }

        CommunityPost post = postMapper.selectById(dto.getPostId());
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        if (post.getStatus() != null && post.getStatus() != 1) {
            throw new BusinessException(400, "该帖子已下架，无法评论");
        }

        // 解析层级：把「回复的回复」扁平化到顶级评论下，只保留一级
        Long parentId = 0L;
        Long replyToUserId = 0L;
        CommunityComment target = resolveTargetComment(dto);
        if (target != null) {
            if (!Objects.equals(target.getPostId(), dto.getPostId())) {
                throw new BusinessException(400, "不能跨帖子回复评论");
            }
            boolean targetIsTop = target.getParentId() == null || target.getParentId() == 0L;
            parentId = targetIsTop ? target.getId() : target.getParentId();
            // 回复顶级评论本身时不显示「回复 @某人」（层级已能表达）；回复子回复时才记录被回复者
            replyToUserId = targetIsTop ? 0L : target.getUserId();
        }

        CommunityComment comment = new CommunityComment();
        comment.setPostId(dto.getPostId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        commentMapper.insert(comment);

        // 原子更新：帖子评论数 +1
        postMapper.update(null, new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, dto.getPostId())
                .setSql("comment_count = comment_count + 1"));
        // 原子更新：顶级评论回复数 +1
        if (parentId != 0L) {
            commentMapper.incrementReplyCount(parentId);
        }

        sendCommentNotifications(post, comment, userId, replyToUserId);

        Map<Long, SysUser> userMap = loadUsers(Collections.singletonList(comment));
        if (replyToUserId != 0L && !userMap.containsKey(replyToUserId)) {
            SysUser replyToUser = userMapper.selectById(replyToUserId);
            if (replyToUser != null) {
                userMap.put(replyToUserId, replyToUser);
            }
        }
        return toVO(comment, userMap, Collections.emptySet(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO updateComment(Long commentId, CommentUpdateDTO dto, Long userId) {
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(400, "评论内容不能超过 " + MAX_COMMENT_LENGTH + " 字");
        }

        CommunityComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        // 归属校验：编辑他人内容永远不允许，管理员也不例外（管理员只能删除）
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException(403, "无权编辑他人评论");
        }

        CommunityComment update = new CommunityComment();
        update.setId(commentId);
        update.setContent(content);
        commentMapper.updateById(update);

        CommunityComment latest = commentMapper.selectById(commentId);
        Map<Long, SysUser> userMap = loadUsers(Collections.singletonList(latest));
        Set<Long> likedIds = loadLikedCommentIds(Collections.singletonList(latest), userId);
        return toVO(latest, userMap, likedIds, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        CommunityComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        boolean isOwner = Objects.equals(comment.getUserId(), userId);
        if (!isOwner && !SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "无权删除他人评论");
        }

        boolean isTopLevel = comment.getParentId() == null || comment.getParentId() == 0L;
        int removed = 1;
        if (isTopLevel) {
            // 顺序不可颠倒：先按 parent_id 清理子回复的点赞，再级联逻辑删除子回复，
            // 否则子回复被标记 deleted 后子查询仍能命中、但语义已混乱
            commentLikeMapper.physicalDeleteByParentComment(commentId);
            // 顶级评论：级联逻辑删除其全部子回复，按实际条数回退帖子计数
            removed += commentMapper.softDeleteRepliesByParent(commentId);
        }
        commentMapper.deleteById(commentId);

        // 原子更新：帖子评论数按实际删除条数回退（下限 0）
        postMapper.update(null, new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(0, comment_count - " + removed + ")"));
        if (!isTopLevel) {
            commentMapper.decrementReplyCount(comment.getParentId(), 1);
        }

        // 清理点赞关系：必须物理删除，逻辑删除的残留行会占住 uk_comment_like 唯一键
        commentLikeMapper.physicalDeleteByComment(commentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentLikeVO toggleLike(Long commentId, Long userId) {
        CommunityComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }

        // 注意：存在性判断与删除都走原生 SQL 物理操作。点赞关系表若用逻辑删除，
        // 残留行会一直占着唯一约束 uk_comment_like，导致「取消点赞后无法再次点赞」。
        Long existId = commentLikeMapper.findLikeId(commentId, userId);
        boolean liked;
        if (existId != null) {
            // 以实际删除行数为准再回退计数，避免并发双删把 like_count 减穿
            if (commentLikeMapper.physicalDelete(commentId, userId) > 0) {
                commentMapper.decrementLikeCount(commentId);
            }
            liked = false;
        } else {
            CommunityCommentLike like = new CommunityCommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            try {
                commentLikeMapper.insert(like);
            } catch (DuplicateKeyException e) {
                // 并发重复点赞：唯一索引兜底，视为已点赞，不重复累加计数
                CommunityComment fresh = commentMapper.selectById(commentId);
                return new CommentLikeVO(true, fresh == null ? 0 : nvl(fresh.getLikeCount()));
            }
            commentMapper.incrementLikeCount(commentId);
            liked = true;
            if (!Objects.equals(comment.getUserId(), userId)) {
                notificationService.createNotification(comment.getUserId(), "like", "收到一个点赞",
                        "你的评论「" + brief(comment.getContent()) + "」收到了一个点赞",
                        comment.getPostId(), "post");
            }
        }
        CommunityComment fresh = commentMapper.selectById(commentId);
        return new CommentLikeVO(liked, fresh == null ? 0 : nvl(fresh.getLikeCount()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByPostId(Long postId) {
        // 先物理清理点赞关系（依赖 community_comment 中尚未被标记删除的行做子查询），再逻辑删除评论
        commentLikeMapper.physicalDeleteByPost(postId);
        return commentMapper.softDeleteByPostId(postId);
    }

    // ==================== 内部工具 ====================

    /** 解析本次回复的目标评论：优先 replyToCommentId，其次 parentId */
    private CommunityComment resolveTargetComment(CommentCreateDTO dto) {
        Long targetId = dto.getReplyToCommentId() != null && dto.getReplyToCommentId() != 0L
                ? dto.getReplyToCommentId()
                : (dto.getParentId() != null && dto.getParentId() != 0L ? dto.getParentId() : null);
        if (targetId == null) {
            return null;
        }
        CommunityComment target = commentMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException(404, "要回复的评论不存在或已删除");
        }
        return target;
    }

    private void applySort(LambdaQueryWrapper<CommunityComment> wrapper, String sortBy) {
        if ("hot".equalsIgnoreCase(sortBy)) {
            wrapper.orderByDesc(CommunityComment::getLikeCount)
                    .orderByDesc(CommunityComment::getCreateTime);
        } else if ("oldest".equalsIgnoreCase(sortBy)) {
            wrapper.orderByAsc(CommunityComment::getCreateTime);
        } else {
            wrapper.orderByDesc(CommunityComment::getCreateTime)
                    .orderByDesc(CommunityComment::getId);
        }
    }

    /** 批量预加载本页顶级评论的回复，按 parentId 分组（时间正序） */
    private Map<Long, List<CommunityComment>> loadReplies(List<CommunityComment> topComments) {
        List<Long> parentIds = topComments.stream()
                .filter(c -> nvl(c.getReplyCount()) > 0)
                .map(CommunityComment::getId)
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CommunityComment> replies = commentMapper.selectList(
                new LambdaQueryWrapper<CommunityComment>()
                        .in(CommunityComment::getParentId, parentIds)
                        .orderByAsc(CommunityComment::getCreateTime)
                        .last("LIMIT " + PRELOAD_REPLY_LIMIT));
        Map<Long, List<CommunityComment>> map = new LinkedHashMap<>();
        for (CommunityComment reply : replies) {
            map.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(reply);
        }
        return map;
    }

    /** 批量加载评论作者与被回复者信息，避免 N+1 查询 */
    private Map<Long, SysUser> loadUsers(List<CommunityComment> comments) {
        Set<Long> userIds = new HashSet<>();
        for (CommunityComment c : comments) {
            if (c.getUserId() != null) {
                userIds.add(c.getUserId());
            }
            if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0L) {
                userIds.add(c.getReplyToUserId());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = userMapper.selectBatchIds(userIds);
        Map<Long, SysUser> map = new HashMap<>(users.size());
        users.forEach(u -> map.put(u.getId(), u));
        return map;
    }

    /** 批量查询当前用户已点赞的评论 ID 集合 */
    private Set<Long> loadLikedCommentIds(List<CommunityComment> comments, Long currentUserId) {
        if (currentUserId == null || comments.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> ids = comments.stream().map(CommunityComment::getId).collect(Collectors.toList());
        List<CommunityCommentLike> likes = commentLikeMapper.selectList(
                new LambdaQueryWrapper<CommunityCommentLike>()
                        .eq(CommunityCommentLike::getUserId, currentUserId)
                        .in(CommunityCommentLike::getCommentId, ids));
        return likes.stream().map(CommunityCommentLike::getCommentId).collect(Collectors.toSet());
    }

    private CommentVO toVO(CommunityComment c, Map<Long, SysUser> userMap, Set<Long> likedIds, Long currentUserId) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setPostId(c.getPostId());
        vo.setUserId(c.getUserId());
        vo.setParentId(nvl(c.getParentId()));
        vo.setReplyToUserId(nvl(c.getReplyToUserId()));
        vo.setContent(c.getContent());
        vo.setLikeCount(nvl(c.getLikeCount()));
        vo.setReplyCount(nvl(c.getReplyCount()));
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());

        SysUser user = userMap.get(c.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0L) {
            SysUser replyTo = userMap.get(c.getReplyToUserId());
            if (replyTo != null) {
                vo.setReplyToNickname(replyTo.getNickname() != null ? replyTo.getNickname() : replyTo.getUsername());
            }
        }

        boolean isAuthor = currentUserId != null && Objects.equals(currentUserId, c.getUserId());
        vo.setLiked(likedIds.contains(c.getId()));
        vo.setAuthor(isAuthor);
        vo.setCanEdit(isAuthor);
        vo.setCanDelete(isAuthor || (currentUserId != null && SecurityUtils.isAdmin()));
        return vo;
    }

    /** 评论 / 回复通知：帖子作者与被回复者各一条，去重且不通知自己 */
    private void sendCommentNotifications(CommunityPost post, CommunityComment comment, Long userId, Long replyToUserId) {
        Set<Long> notified = new HashSet<>();
        notified.add(userId);

        if (replyToUserId != 0L && !notified.contains(replyToUserId)) {
            notificationService.createNotification(replyToUserId, "comment", "有人回复了你",
                    "你的评论收到新回复：" + brief(comment.getContent()), post.getId(), "post");
            notified.add(replyToUserId);
        }
        if (post.getUserId() != null && !notified.contains(post.getUserId())) {
            String title = comment.getParentId() == 0L ? "收到一条评论" : "帖子有了新回复";
            notificationService.createNotification(post.getUserId(), "comment", title,
                    "你的帖子《" + post.getTitle() + "》收到了新评论：" + brief(comment.getContent()),
                    post.getId(), "post");
        }
    }

    private String brief(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30) + "…";
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
