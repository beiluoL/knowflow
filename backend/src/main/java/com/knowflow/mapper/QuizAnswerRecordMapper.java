package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.QuizAnswerRecord;
import org.apache.ibatis.annotations.Mapper;

/** 在线答题记录表数据访问层。 */
@Mapper
public interface QuizAnswerRecordMapper extends BaseMapper<QuizAnswerRecord> {
}
