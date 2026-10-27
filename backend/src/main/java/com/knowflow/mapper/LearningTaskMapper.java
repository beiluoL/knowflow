package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习任务表数据访问层，基于 MyBatis-Plus 提供学习任务的增删改查。
 */
@Mapper
public interface LearningTaskMapper extends BaseMapper<LearningTask> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM learning_task WHERE id=#{id} AND user_id=#{user} AND deleted=0 FOR UPDATE")
    LearningTask lockOwned(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("user") Long user);

}
