package com.knowflow.techenglish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.techenglish.entity.TechVocabPronunciation;
import org.apache.ibatis.annotations.Mapper;

/** 技术英语发音变体 Mapper */
@Mapper
public interface TechVocabPronunciationMapper extends BaseMapper<TechVocabPronunciation> {
    /**
     * 替换词条发音前移除全部旧子行，包含逻辑删除行，释放原变体唯一键。
     * 仅供已完成词条权限验证的业务事务调用；返回实际物理删除数，允许为0。
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM tech_vocab_pronunciation WHERE term_id = #{termId}")
    int deleteAllForTermReplacement(@org.apache.ibatis.annotations.Param("termId") Long termId);
}
