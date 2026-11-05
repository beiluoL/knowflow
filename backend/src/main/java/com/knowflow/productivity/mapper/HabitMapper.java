package com.knowflow.productivity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.productivity.entity.Habit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 习惯表数据访问层。
 */
@Mapper
public interface HabitMapper extends BaseMapper<Habit> {
}
