package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.DocCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档分类表数据访问层，基于 MyBatis-Plus 提供文档分类的增删改查。
 */
@Mapper
public interface DocCategoryMapper extends BaseMapper<DocCategory> {
}
