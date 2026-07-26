package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningFlashcard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习闪卡表数据访问层，基于 MyBatis-Plus 提供闪卡的增删改查。
 */
@Mapper
public interface LearningFlashcardMapper extends BaseMapper<LearningFlashcard> {
}
