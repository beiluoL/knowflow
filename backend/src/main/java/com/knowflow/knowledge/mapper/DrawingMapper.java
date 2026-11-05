package com.knowflow.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.knowledge.entity.Drawing;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供绘图记录的数据访问映射。
 */
@Mapper
public interface DrawingMapper extends BaseMapper<Drawing> {
}
