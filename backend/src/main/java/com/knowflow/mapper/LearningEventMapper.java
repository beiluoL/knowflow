package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningEvent;
import org.apache.ibatis.annotations.Mapper;

/** 学习行为事件 Mapper（Learning Event System，Phase 1）。 */
@Mapper
public interface LearningEventMapper extends BaseMapper<LearningEvent> {
}
