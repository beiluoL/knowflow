package com.knowflow.knowledge.contract;

/** Read-only document material for a project-context snapshot, authorized by the knowledge owner. */
public interface ProjectDocumentQuery {
    /**
     * Reads current document content after the existing document access checks.
     * Missing/deleted documents retain 404; unreadable documents retain 401/403.
     * No grading, project-code verification, or persistence is performed.
     */
    Content requireReadable(long documentId, long actor);

    /** Nullable source fields are preserved; the consuming snapshot applies its own display bounds. */
    record Content(String title, String content) {}
}
