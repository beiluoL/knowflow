package com.knowflow.learning.mapper;



import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.learning.entity.LearningGoal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习目标 Mapper。
 */
@Mapper
public interface LearningGoalMapper extends BaseMapper<LearningGoal> {
}
