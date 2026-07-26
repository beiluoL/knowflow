package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningPath;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习路径表数据访问层，基于 MyBatis-Plus 提供学习路径的增删改查。
 */
@Mapper
public interface LearningPathMapper extends BaseMapper<LearningPath> {
}
