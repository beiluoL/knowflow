package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.LearningUserPath;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户学习路径关联表数据访问层，基于 MyBatis-Plus 提供用户与路径关联的增删改查。
 */
@Mapper
public interface LearningUserPathMapper extends BaseMapper<LearningUserPath> {
}
