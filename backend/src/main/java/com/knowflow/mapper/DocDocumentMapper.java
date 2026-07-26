package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.DocDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档表数据访问层，基于 MyBatis-Plus 提供文档的增删改查与分页。
 */
@Mapper
public interface DocDocumentMapper extends BaseMapper<DocDocument> {
}
