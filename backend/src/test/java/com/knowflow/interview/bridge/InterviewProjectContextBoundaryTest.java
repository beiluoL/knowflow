package com.knowflow.interview.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 项目快照消费知识模块所有者提供的投影，不直接使用实体或 Mapper。 */
class InterviewProjectContextBoundaryTest {
    @Test
    void projectContextUsesOnlyTheKnowledgeReadContract() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/knowflow/interview/bridge/InterviewProjectContextService.java"));
        assertFalse(source.contains("com.knowflow.knowledge.mapper."));
        assertFalse(source.contains("com.knowflow.knowledge.entity."));
        assertFalse(source.contains("DocumentAccessPolicy"));
        assertTrue(source.contains("com.knowflow.knowledge.contract.ProjectDocumentQuery"));
    }
}
