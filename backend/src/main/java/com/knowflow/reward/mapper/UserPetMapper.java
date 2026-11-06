package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.UserPet;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习宠物 Mapper：user_pet 表的基础 CRUD，无自定义 SQL。
 *
 * <p>调用方 UserPetService 用 selectOne / insert / updateById 完成默认宠物创建、喂食玩耍与专注结算；DDL 只有普通索引
 * idx_user_pet_user，没有 user_id 唯一约束。
 *
 * <p>「一人一宠」不是数据库约束，而是 Service 先用 RewardMemberAccess#lockActiveUserId 锁住 sys_user 行，把并发的首次创建串行化；实体继承
 * BaseEntity，查询自动过滤 deleted=0。
 */
@Mapper
public interface UserPetMapper extends BaseMapper<UserPet> {}
