package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningNote;
import org.apache.ibatis.annotations.Mapper;

/** 章节学习笔记 Mapper */
@Mapper
public interface LearningNoteMapper extends BaseMapper<LearningNote> {
}
