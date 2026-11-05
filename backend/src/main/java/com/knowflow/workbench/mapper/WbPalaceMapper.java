package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbPalace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供记忆宫殿的数据访问映射。
 */
@Mapper
public interface WbPalaceMapper extends BaseMapper<WbPalace> {
}
