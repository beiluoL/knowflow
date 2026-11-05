package com.knowflow.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.exam.entity.ExamTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供组卷模板的数据访问映射。
 */
@Mapper
public interface ExamTemplateMapper extends BaseMapper<ExamTemplate> {
}
