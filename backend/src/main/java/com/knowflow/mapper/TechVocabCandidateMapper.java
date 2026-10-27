package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.TechVocabCandidate;
import org.apache.ibatis.annotations.Mapper;

/** 技术英语个人候选词条 Mapper */
@Mapper
public interface TechVocabCandidateMapper extends BaseMapper<TechVocabCandidate> {
}
