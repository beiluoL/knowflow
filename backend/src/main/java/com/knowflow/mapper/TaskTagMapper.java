package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.TaskTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务标签数据访问层。
 */
@Mapper
public interface TaskTagMapper extends BaseMapper<TaskTag> {
}
