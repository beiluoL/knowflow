package com.knowflow.mastery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.mastery.entity.ResourceKnowledgeMapping;
import org.apache.ibatis.annotations.Mapper;

/** 资源→知识点映射 Mapper（Knowledge Mastery Engine，Phase 2-B）。 */
@Mapper
public interface ResourceKnowledgeMappingMapper extends BaseMapper<ResourceKnowledgeMapping> {
}
