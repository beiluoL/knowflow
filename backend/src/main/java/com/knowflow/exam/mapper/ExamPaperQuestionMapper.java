package com.knowflow.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.exam.entity.ExamPaperQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供试卷题目快照的数据访问映射。
 */
@Mapper
public interface ExamPaperQuestionMapper extends BaseMapper<ExamPaperQuestion> {
}
