package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.HabitCheckIn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 习惯打卡记录表数据访问层。
 */
@Mapper
public interface HabitCheckInMapper extends BaseMapper<HabitCheckIn> {
}
