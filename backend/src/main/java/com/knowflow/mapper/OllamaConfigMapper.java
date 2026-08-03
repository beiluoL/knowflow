package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.OllamaConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Ollama 配置表数据访问层。
 */
@Mapper
public interface OllamaConfigMapper extends BaseMapper<OllamaConfig> {
}
