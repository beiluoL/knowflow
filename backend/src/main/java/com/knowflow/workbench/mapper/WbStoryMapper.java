package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbStory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供记忆故事的数据访问映射。
 */
@Mapper
public interface WbStoryMapper extends BaseMapper<WbStory> {
}
