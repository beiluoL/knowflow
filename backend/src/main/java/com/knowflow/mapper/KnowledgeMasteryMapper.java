package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.KnowledgeMastery;
import org.apache.ibatis.annotations.Mapper;

/** 知识点掌握度 Mapper（Knowledge Mastery Engine，Phase 2-B）。 */
@Mapper
public interface KnowledgeMasteryMapper extends BaseMapper<KnowledgeMastery> {
}
