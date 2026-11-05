package com.knowflow.productivity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.productivity.entity.CalendarMemorial;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供日历纪念日记录的数据访问映射。
 */
@Mapper
public interface CalendarMemorialMapper extends BaseMapper<CalendarMemorial> {
}
