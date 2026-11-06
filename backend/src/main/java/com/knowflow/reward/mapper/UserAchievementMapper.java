package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.UserAchievement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户成就解锁记录 Mapper：user_achievement 表的基础 CRUD，无自定义 SQL。
 *
 * <p>调用方 AchievementServiceImpl：按 userId 查已有解锁行，达标时 insert 一行；插入与 sys_user.exp 的绝对赋值处于同一事务，失败整体回滚。
 *
 * <p>唯一索引 idx_ua_user_ach(user_id, achievement_id) 不含 deleted，且实体不继承 BaseEntity、deleted 没有
 * {@code @TableLogic}：软删行仍占唯一键，重复或并发解锁会抛 DuplicateKeyException，当前插入处未捕获。
 */
@Mapper
public interface UserAchievementMapper extends BaseMapper<UserAchievement> {}
