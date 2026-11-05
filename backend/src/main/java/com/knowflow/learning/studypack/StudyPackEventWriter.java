package com.knowflow.learning.studypack;

/** Persists ordered job and step transition events using the caller transaction. */
final class StudyPackEventWriter {
    private StudyPackEventWriter() {}

    static void event(
            org.springframework.jdbc.core.JdbcTemplate sql,
            long id,
            String kind,
            String state,
            Long ref,
            Long elapsed) {
        sql.update(
                "INSERT INTO study_pack_event(job_id,kind,state,resource_id,elapsed_ms,create_time)"
                        + " VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
                id,
                kind,
                state,
                ref,
                elapsed);
    }
}
