package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.AiLearningSuggestion;
import org.apache.ibatis.annotations.Mapper;

/** AI 学习建议缓存 Mapper */
@Mapper
public interface AiLearningSuggestionMapper extends BaseMapper<AiLearningSuggestion> {
}
