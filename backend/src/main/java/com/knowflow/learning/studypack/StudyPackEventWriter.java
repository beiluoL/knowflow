package com.knowflow.learning.studypack;

/** 使用调用方事务持久化有序任务及步骤迁移事件。 */
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
