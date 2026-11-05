CREATE TABLE IF NOT EXISTS rag_index_revision (
  doc_id BIGINT PRIMARY KEY COMMENT '知识库文档 ID',
  revision BIGINT NOT NULL COMMENT '最近一次已提交索引版本',
  chunk_count INT NOT NULL COMMENT '该版本分块数'
);
