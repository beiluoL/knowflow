package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningMistake;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习错题表数据访问层，基于 MyBatis-Plus 提供错题的增删改查。
 */
@Mapper
public interface LearningMistakeMapper extends BaseMapper<LearningMistake> {
}
