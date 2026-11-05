package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbReviewLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供工作台复习日志的数据访问映射。
 */
@Mapper
public interface WbReviewLogMapper extends BaseMapper<WbReviewLog> {
}
