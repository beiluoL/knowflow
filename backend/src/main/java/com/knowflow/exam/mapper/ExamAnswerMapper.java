package com.knowflow.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.exam.entity.ExamAnswer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供考试答案的数据访问映射。
 */
@Mapper
public interface ExamAnswerMapper extends BaseMapper<ExamAnswer> {
}
