package com.knowflow.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.knowledge.entity.KgRelation;
import org.apache.ibatis.annotations.Mapper;

/** 实体关系表 Mapper。 */
@Mapper
public interface KgRelationMapper extends BaseMapper<KgRelation> {
}
