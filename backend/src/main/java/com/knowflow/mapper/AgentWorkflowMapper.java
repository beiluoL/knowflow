package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.AgentWorkflow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自定义工作流配置数据访问层。
 */
@Mapper
public interface AgentWorkflowMapper extends BaseMapper<AgentWorkflow> {
}
