package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbCapture;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供工作台采集记录的数据访问映射。
 */
@Mapper
public interface WbCaptureMapper extends BaseMapper<WbCapture> {
}
