package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.TaskList;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务清单表数据访问层，基于 MyBatis-Plus 提供清单的增删改查。
 */
@Mapper
public interface TaskListMapper extends BaseMapper<TaskList> {
}
