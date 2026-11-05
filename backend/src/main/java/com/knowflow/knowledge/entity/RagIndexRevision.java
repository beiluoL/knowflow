package com.knowflow.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 文档当前已提交的分块版本。 */
@Data
@TableName("rag_index_revision")
public class RagIndexRevision {
  @TableId(type = IdType.INPUT)
  private Long docId;

  private Long revision;
  private Integer chunkCount;
}
