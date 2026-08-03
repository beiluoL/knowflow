package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.AgentToolCall;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用链明细数据访问层。
 */
@Mapper
public interface AgentToolCallMapper extends BaseMapper<AgentToolCall> {
}
