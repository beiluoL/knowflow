package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档分块实体：存储文档切分后的片断内容及其 embedding 向量。
 */
@Data
@TableName("doc_chunk")
public class DocChunk {

    private Long id;
    /** 归属文档ID */
    private Long docId;
    /** 分块序号 */
    private Integer chunkIndex;
    /** 分块文本内容 */
    private String content;
    /** 字符数 */
    private Integer charCount;
    /** embedding 向量：逗号分隔浮点数 */
    private String embedding;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
}
