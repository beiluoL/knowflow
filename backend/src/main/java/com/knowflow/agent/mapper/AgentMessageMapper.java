package com.knowflow.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.agent.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编程 Agent 消息表数据访问层。
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
}
