package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.UserAchievement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户成就解锁记录 Mapper
 */
@Mapper
public interface UserAchievementMapper extends BaseMapper<UserAchievement> {
}
