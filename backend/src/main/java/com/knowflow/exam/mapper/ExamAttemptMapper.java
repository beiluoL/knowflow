package com.knowflow.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.exam.entity.ExamAttempt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供考试作答记录的数据访问映射。
 */
@Mapper
public interface ExamAttemptMapper extends BaseMapper<ExamAttempt> {
}
