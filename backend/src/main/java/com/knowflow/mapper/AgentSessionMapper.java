package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.AgentSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编程 Agent 会话表数据访问层。
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {
}
