package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningChapter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习章节表数据访问层，基于 MyBatis-Plus 提供学习章节的增删改查。
 */
@Mapper
public interface LearningChapterMapper extends BaseMapper<LearningChapter> {
}
