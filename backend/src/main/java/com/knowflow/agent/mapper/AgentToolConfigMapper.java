package com.knowflow.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.agent.entity.AgentToolConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具启用配置数据访问层。
 */
@Mapper
public interface AgentToolConfigMapper extends BaseMapper<AgentToolConfig> {
}
