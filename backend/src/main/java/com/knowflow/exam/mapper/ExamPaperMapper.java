package com.knowflow.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.exam.entity.ExamPaper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供试卷记录的数据访问映射。
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {
}
