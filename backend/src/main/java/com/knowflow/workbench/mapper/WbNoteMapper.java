package com.knowflow.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.workbench.entity.WbNote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WbNoteMapper extends BaseMapper<WbNote> {
}
