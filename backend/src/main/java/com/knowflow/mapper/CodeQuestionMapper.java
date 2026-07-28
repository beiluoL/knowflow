package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.CodeQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码题目表数据访问层，基于 MyBatis-Plus 提供题目的增删改查。
 */
@Mapper
public interface CodeQuestionMapper extends BaseMapper<CodeQuestion> {
}
