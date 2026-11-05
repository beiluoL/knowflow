package com.knowflow.learning.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.learning.entity.ChapterFavorite;
import com.knowflow.learning.mapper.ChapterFavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 章节收藏业务服务。
 *
 * <p><b>业务语义</b>：收藏采用 toggle 语义 —— 同一用户对同一章节重复调用时，
 * 在「已收藏 / 未收藏」之间切换，不是幂等新增。</p>
 *
 * <p><b>数据一致性约束</b>：{@code chapter_favorite} 使用逻辑删除
 * （{@code @TableLogic deleted}）。取消收藏只把 {@code deleted} 置 1，物理行仍在。
 * 再次收藏必须<b>复活原行</b>（{@code UPDATE deleted = 0}），而不是 INSERT 新行；
 * 否则每轮「收藏 → 取消 → 收藏」都会留下一行永不清理的垃圾数据。</p>
 *
 * <p><b>为什么不用 {@code selectOne}</b>：历史数据可能因并发双写成同一
 * {@code (user_id, chapter_id)} 多行（该表当前<b>没有</b>唯一索引，见
 * {@code docs/architecture/CODE_AUDIT_REPORT.md} A-03）。{@code selectOne}
 * 在多行时抛异常会把一次正常收藏变成 500，因此统一走
 * {@link ChapterFavoriteMapper#findAnyRowId} 的 {@code LIMIT 1}。</p>
 */
@Service
@RequiredArgsConstructor
public class ChapterFavoriteService {

    private final ChapterFavoriteMapper favoriteMapper;

    private final LearningService learningService;

    /**
     * 切换指定章节的收藏状态。
     *
     * <p>先校验章节存在且当前用户可读（不存在返回 404，无权返回 403），
     * 再执行「已收藏则取消，未收藏则收藏或复活历史行」。</p>
     *
     * @param userId    当前登录用户 ID，必须来自认证上下文
     * @param chapterId 章节 ID
     * @return {@code true} 表示操作后处于已收藏态；{@code false} 表示已取消收藏
     * @throws com.knowflow.exception.BusinessException 章节不存在（404）或无权访问（403）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggle(Long userId, Long chapterId) {
        Objects.requireNonNull(userId, "userId 必须来自认证上下文");
        Objects.requireNonNull(chapterId, "chapterId 不能为空");
        // 复用章节详情做存在性与可读性校验，保持接口既有 404/403 语义。
        learningService.getChapterDetail(chapterId, userId);
        if (favoriteMapper.lockChapter(chapterId) == null) {
            throw new com.knowflow.exception.BusinessException(404, "章节不存在");
        }


        Long rowId = favoriteMapper.findAnyRowId(userId, chapterId);
        if (rowId == null) {
            favoriteMapper.insert(newFavorite(userId, chapterId));
            return true;
        }

        Integer deleted = favoriteMapper.findDeletedFlag(rowId);
        if (deleted != null && deleted == 0) {
            // 处于已收藏态：逻辑删除即「取消收藏」。
            if (favoriteMapper.deleteById(rowId) != 1) throw new com.knowflow.exception.BusinessException(409, "收藏状态已变化");
            return false;
        }

        // 处于已删除态：复活原行，不新增物理行。
        if (favoriteMapper.reviveById(rowId) != 1) throw new com.knowflow.exception.BusinessException(409, "收藏状态已变化");
        return true;
    }

    /**
     * 查询指定章节是否已被当前用户收藏。
     *
     * @param userId    当前登录用户 ID
     * @param chapterId 章节 ID
     * @return 已收藏返回 {@code true}
     * @throws com.knowflow.exception.BusinessException 章节不存在（404）或无权访问（403）
     */
    public boolean isFavorited(Long userId, Long chapterId) {
        Objects.requireNonNull(userId, "userId 必须来自认证上下文");
        Objects.requireNonNull(chapterId, "chapterId 不能为空");
        learningService.getChapterDetail(chapterId, userId);

        Long rowId = favoriteMapper.findAnyRowId(userId, chapterId);
        if (rowId == null) {
            return false;
        }
        Integer deleted = favoriteMapper.findDeletedFlag(rowId);
        return deleted != null && deleted == 0;
    }

    /**
     * 查询当前用户收藏的全部章节 ID，按收藏时间倒序。
     *
     * @param userId 当前登录用户 ID
     * @return 章节 ID 列表；无收藏时返回空列表（不是 {@code null}）
     */
    public List<Long> listFavoriteChapterIds(Long userId) {
        Objects.requireNonNull(userId, "userId 必须来自认证上下文");
        return favoriteMapper.selectList(new LambdaQueryWrapper<ChapterFavorite>()
                        .eq(ChapterFavorite::getUserId, userId)
                        .orderByDesc(ChapterFavorite::getCreateTime))
                .stream()
                .map(ChapterFavorite::getChapterId)
                .toList();
    }

    /** 构造一条新的收藏记录（owner 由服务端上下文注入，不接受前端传参）。 */
    private ChapterFavorite newFavorite(Long userId, Long chapterId) {
        ChapterFavorite favorite = new ChapterFavorite();
        favorite.setUserId(userId);
        favorite.setChapterId(chapterId);
        return favorite;
    }
}
