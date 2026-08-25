package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.Habit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 习惯表数据访问层。
 */
@Mapper
public interface HabitMapper extends BaseMapper<Habit> {
}
