package com.knowflow.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.knowledge.mapper.RagIndexRevisionMapper;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.knowledge.service.DocChunkService;
import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 验证检索索引替换失败时保留旧分块与版本。
 */
@SpringBootTest
@ActiveProfiles("local")
class RagIndexPublicationTest {
  @Autowired DocDocumentMapper documents;
  @Autowired DocChunkService index;
  @Autowired RagIndexRevisionMapper revisions;
  @Autowired JdbcTemplate db;

  @Test
  void failedReplacementKeepsOldChunksAndVersion() {
    var doc = new DocDocument();
    doc.setTitle("RAG index publication test");
    documents.insert(doc);
    long id = doc.getId();
    index.indexDocument(id, "original evidence");
    assertEquals(1L, revisions.selectById(id).getRevision());
    db.execute(
        "CREATE TRIGGER rag_reject_chunk BEFORE INSERT ON doc_chunk "
            + "FOR EACH ROW CALL 'com.knowflow.knowledge.rag.RagIndexPublicationTest$RejectChunk'");
    try {
      assertThrows(RuntimeException.class, () -> index.indexDocument(id, "replacement evidence"));
    } finally {
      db.execute("DROP TRIGGER rag_reject_chunk");
    }
    assertEquals(1L, revisions.selectById(id).getRevision());
    assertEquals(
        "original evidence",
        db.queryForObject(
            "SELECT content FROM doc_chunk WHERE doc_id = ? AND deleted = 0", String.class, id));
    index.indexDocument(id, "replacement evidence");
    assertEquals(2L, revisions.selectById(id).getRevision());
    assertEquals(
        "replacement evidence",
        db.queryForObject(
            "SELECT content FROM doc_chunk WHERE doc_id = ? AND deleted = 0", String.class, id));
  }

  public static class RejectChunk implements Trigger {
    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
      throw new SQLException("injected chunk insert failure");
    }
  }
}
