package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务表数据访问层，基于 MyBatis-Plus 提供任务的增删改查。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
