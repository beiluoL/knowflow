package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习任务表数据访问层，基于 MyBatis-Plus 提供学习任务的增删改查。
 */
@Mapper
public interface LearningTaskMapper extends BaseMapper<LearningTask> {
}
