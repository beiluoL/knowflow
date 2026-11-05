package com.knowflow.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.learning.entity.QuizQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供学习题目及关联查询的数据访问映射。
 */
@Mapper
public interface QuizQuestionMapper extends BaseMapper<QuizQuestion> {
}
