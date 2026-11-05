package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbRecallSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供回忆练习会话的数据访问映射。
 */
@Mapper
public interface WbRecallSessionMapper extends BaseMapper<WbRecallSession> {
}
