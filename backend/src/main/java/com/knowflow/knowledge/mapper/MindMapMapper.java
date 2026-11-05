package com.knowflow.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.knowledge.entity.MindMap;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供思维导图的数据访问映射。
 */
@Mapper
public interface MindMapMapper extends BaseMapper<MindMap> {
}
