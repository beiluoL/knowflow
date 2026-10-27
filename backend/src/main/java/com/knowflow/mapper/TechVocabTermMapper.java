package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.TechVocabTerm;
import org.apache.ibatis.annotations.Mapper;

/** 技术英语词条 Mapper */
@Mapper
public interface TechVocabTermMapper extends BaseMapper<TechVocabTerm> {
}
