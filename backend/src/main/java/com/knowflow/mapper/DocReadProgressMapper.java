package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.DocReadProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档阅读进度表数据访问层，基于 MyBatis-Plus 提供阅读进度的增删改查。
 */
@Mapper
public interface DocReadProgressMapper extends BaseMapper<DocReadProgress> {
}
