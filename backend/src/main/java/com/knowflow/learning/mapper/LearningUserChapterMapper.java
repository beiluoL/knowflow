package com.knowflow.learning.mapper;



import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.learning.entity.LearningUserChapter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供用户章节进度的数据访问映射。
 */
@Mapper
public interface LearningUserChapterMapper extends BaseMapper<LearningUserChapter> {
}
