package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.UserAchievement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户成就解锁记录 Mapper
 */
@Mapper
public interface UserAchievementMapper extends BaseMapper<UserAchievement> {
}
