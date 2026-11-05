package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbPalaceLoci;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供记忆宫殿位置的数据访问映射。
 */
@Mapper
public interface WbPalaceLociMapper extends BaseMapper<WbPalaceLoci> {
}
