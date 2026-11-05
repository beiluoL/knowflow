package com.knowflow.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.knowledge.entity.DocDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档表数据访问层，基于 MyBatis-Plus 提供文档的增删改查与分页。
 */
@Mapper
public interface DocDocumentMapper extends BaseMapper<DocDocument> {
    /** 包含软删除引用，避免删除私有文档后其文件变成公开资源。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM doc_document WHERE file_url=#{url} OR cover=#{url} OR content LIKE #{pattern} ESCAPE '!'")
    java.util.List<DocDocument> findAssetReferences(@org.apache.ibatis.annotations.Param("url") String url,@org.apache.ibatis.annotations.Param("pattern") String pattern);
}
