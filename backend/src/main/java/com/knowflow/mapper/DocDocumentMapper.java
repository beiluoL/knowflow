package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.DocDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档表数据访问层，基于 MyBatis-Plus 提供文档的增删改查与分页。
 */
@Mapper
public interface DocDocumentMapper extends BaseMapper<DocDocument> {
    /** Include soft-deleted references: deleting a private document must not publish its file. */
    @org.apache.ibatis.annotations.Select("SELECT * FROM doc_document WHERE file_url=#{url} OR cover=#{url} OR content LIKE #{pattern} ESCAPE '!'")
    java.util.List<DocDocument> findAssetReferences(@org.apache.ibatis.annotations.Param("url") String url,@org.apache.ibatis.annotations.Param("pattern") String pattern);
}
