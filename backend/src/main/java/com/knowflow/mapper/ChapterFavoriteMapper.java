package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.ChapterFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 章节收藏数据访问接口。
 *
 * <p><b>为什么需要原生 SQL</b>：{@link ChapterFavorite} 继承 {@code BaseEntity}，
 * 带 {@code @TableLogic deleted}。MyBatis-Plus 生成的查询会自动追加 {@code deleted = 0}，
 * 导致「查不到已被逻辑删除的历史行」。取消收藏后再收藏时若只走默认查询，
 * 就会误判为「从未收藏」而重新 INSERT，使表内物理行随切换次数无限增长。
 * 因此复活逻辑必须用原生 SQL 绕开逻辑删除过滤。</p>
 */
@Mapper
public interface ChapterFavoriteMapper extends BaseMapper<ChapterFavorite> {

    /** 锁定章节作为收藏键的稳定互斥点；覆盖尚无收藏行的并发首次写入。 */
    @Select("SELECT id FROM learning_chapter WHERE id = #{chapterId} AND deleted = 0 FOR UPDATE")
    Long lockChapter(@Param("chapterId") Long chapterId);

    /**
     * 查询该用户与该章节的任意一条收藏行主键（**包含已逻辑删除的行**）。
     *
     * <p>排序为 {@code deleted ASC, id ASC}：优先命中未删除行，其次命中最早的历史行，
     * 保证「复活」时复用同一物理行。使用 {@code LIMIT 1} 而非 {@code selectOne}，
     * 避免历史脏数据存在多行时抛 {@code TooManyResultsException}。</p>
     *
     * @param userId    用户 ID
     * @param chapterId 章节 ID
     * @return 收藏行主键；该组合不存在任何物理行时返回 {@code null}
     */
    @Select("SELECT id FROM chapter_favorite "
            + "WHERE user_id = #{userId} AND chapter_id = #{chapterId} "
            + "ORDER BY deleted ASC, id ASC LIMIT 1 FOR UPDATE")
    Long findAnyRowId(@Param("userId") Long userId, @Param("chapterId") Long chapterId);

    /**
     * 读取指定收藏行的逻辑删除标记（原生 SQL，不受 {@code @TableLogic} 过滤）。
     *
     * @param id 收藏行主键
     * @return {@code 0} 未删除 / {@code 1} 已删除；行不存在时返回 {@code null}
     */
    @Select("SELECT deleted FROM chapter_favorite WHERE id = #{id} FOR UPDATE")
    Integer findDeletedFlag(@Param("id") Long id);

    /**
     * 复活一条已逻辑删除的收藏行：把 {@code deleted} 置回 0。
     *
     * <p>只更新 {@code deleted = 1} 的行，避免把已处于收藏态的行重复改写。</p>
     *
     * @param id 收藏行主键
     * @return 实际更新行数；0 表示该行不存在或本就未删除
     */
    @Update("UPDATE chapter_favorite SET deleted = 0, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 1")
    int reviveById(@Param("id") Long id);
}
