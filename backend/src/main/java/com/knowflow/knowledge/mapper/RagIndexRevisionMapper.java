package com.knowflow.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.knowledge.entity.RagIndexRevision;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提供检索增强索引版本的数据访问映射。
 */
@Mapper
public interface RagIndexRevisionMapper extends BaseMapper<RagIndexRevision> {
  @Insert(
      """
      INSERT INTO rag_index_revision (doc_id, revision, chunk_count)
      VALUES (#{docId}, 1, #{chunkCount})
      ON DUPLICATE KEY UPDATE revision = revision + 1, chunk_count = #{chunkCount}
      """)
  int publish(@Param("docId") Long docId, @Param("chunkCount") int chunkCount);
}
