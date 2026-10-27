package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.DocFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 文档收藏表数据访问层，基于 MyBatis-Plus 提供文档收藏的增改查。
 *
 * <p>本表<b>保留逻辑删除</b>（{@link DocFavorite} 经 {@code BaseEntity} 带 {@code @TableLogic}），
 * 且唯一约束 {@code uk_fav_user_doc(user_id, doc_id)} <b>不含 deleted 列</b>。两者叠加后：
 * 取消收藏只把 {@code deleted} 置 1，物理行仍在并继续占用唯一键；若再次收藏直接
 * {@code INSERT}，就会撞唯一键抛 {@code DuplicateKeyException}（表现为 HTTP 500），
 * 且该文档此后<b>永久无法再被收藏</b>。因此再次收藏必须「复活」原行而不是插入新行，
 * 复活 SQL 需绕过 MyBatis-Plus 自动追加的 {@code deleted = 0} 条件。</p>
 */
@Mapper
public interface DocFavoriteMapper extends BaseMapper<DocFavorite> {

    /**
     * 复活已被逻辑删除的收藏行：把 {@code deleted} 由 1 置回 0。
     *
     * <p>用原生 SQL 直写，因此不受 MyBatis-Plus 逻辑删除条件（{@code AND deleted = 0}）影响；
     * 同时显式维护 {@code update_time}（原生 SQL 不会触发 {@code FieldFill} 填充）。
     * 条件里带 {@code deleted = 1}，既避免把已收藏的行重复计数，也让并发场景下
     * 「已被其它请求复活」的那次调用返回 0 行。</p>
     *
     * @param userId     收藏人 id
     * @param docId      文档 id
     * @param updateTime 本次复活时间，写入 {@code update_time}
     * @return 实际复活行数；0 表示不存在逻辑删除行（调用方据此决定是否需要 INSERT）
     */
    @Update("UPDATE doc_favorite SET deleted = 0, update_time = #{updateTime} "
            + "WHERE user_id = #{userId} AND doc_id = #{docId} AND deleted = 1")
    int reviveDeleted(@Param("userId") Long userId,
                      @Param("docId") Long docId,
                      @Param("updateTime") LocalDateTime updateTime);
}
