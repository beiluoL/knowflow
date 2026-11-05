package com.knowflow.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.ai.entity.UserAiConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供用户 AI 配置的数据访问映射。
 */
@Mapper
public interface UserAiConfigMapper extends BaseMapper<UserAiConfig> {
}
