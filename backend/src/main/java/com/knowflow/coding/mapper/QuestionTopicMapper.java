package com.knowflow.coding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.coding.entity.QuestionTopic;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供题目主题关联的数据访问映射。
 */
@Mapper
public interface QuestionTopicMapper extends BaseMapper<QuestionTopic> {
}
