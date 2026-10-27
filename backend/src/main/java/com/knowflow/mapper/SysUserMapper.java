package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户表数据访问层，基于 MyBatis-Plus 提供用户的增删改查。
 *
 * <p><b>P2-User-Register-After-Delete</b>：{@code sys_user} 的两个业务唯一键
 * （{@code username} 列级 UNIQUE + {@code uk_user_email}）都<b>不含 deleted 列</b>，
 * 而 {@code @TableLogic} 会给所有 MP 生成的 SQL 追加 {@code deleted = 0}。
 * 二者叠加导致「逻辑删除行的唯一键残留」既<b>查不到</b>（selectOne 被过滤），
 * 也<b>改不动</b>（updateById 被追加 {@code deleted = 0} → 静默更新 0 行）。
 * 因此该场景必须走下面的原生 SQL，禁止用 MP 的 selectOne / updateById 替代。</p>
 *
 * @see com.knowflow.service.impl.UserServiceImpl
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询占用某 username 的<b>已逻辑删除</b>历史行（不受 @TableLogic 过滤）。
     *
     * @param username 待释放的用户名
     * @return 历史行列表（按 id 升序，通常 0~1 行）
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 1 ORDER BY id")
    List<SysUser> selectDeletedHoldersByUsername(@Param("username") String username);

    /**
     * 查询占用某 email 的<b>已逻辑删除</b>历史行（不受 @TableLogic 过滤）。
     *
     * @param email 待释放的邮箱
     * @return 历史行列表（按 id 升序，通常 0~1 行）
     */
    @Select("SELECT * FROM sys_user WHERE email = #{email} AND deleted = 1 ORDER BY id")
    List<SysUser> selectDeletedHoldersByEmail(@Param("email") String email);

    /**
     * 释放唯一键占用：把 username / email 改写为归档值。
     *
     * @param id         目标行主键（已逻辑删除的用户）
     * @param username   归档后的用户名（非空，长度 ≤ 50）
     * @param email      归档后的邮箱（可为 {@code null}：唯一索引中 NULL 不占用槽位）
     * @param updateTime 更新时间
     * @return 实际更新行数
     */
    @Update("UPDATE sys_user SET username = #{username}, email = #{email}, update_time = #{updateTime} "
            + "WHERE id = #{id}")
    int releaseUniqueKeys(@Param("id") Long id,
                          @Param("username") String username,
                          @Param("email") String email,
                          @Param("updateTime") LocalDateTime updateTime);
}
