package com.knowflow.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.learning.entity.QuizQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuizQuestionMapper extends BaseMapper<QuizQuestion> {
}
