package com.knowflow.productivity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.productivity.entity.TaskTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务标签数据访问层。
 */
@Mapper
public interface TaskTagMapper extends BaseMapper<TaskTag> {
}
