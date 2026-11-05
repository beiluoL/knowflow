package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供工作台笔记的数据访问映射。
 */
@Mapper
public interface WbNoteMapper extends BaseMapper<WbNote> {
}
