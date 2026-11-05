package com.knowflow.interview.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Project snapshots consume the knowledge owner's projection, never its entity or mapper. */
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
