package com.knowflow.interview.generation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/** Phase 1 增量表；只新增，不改写既有会话、题库或评分快照。 */
@Component
@RequiredArgsConstructor
public class InterviewTrainerSchema {

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void initialize() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String dialect = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2") ? "h2" : "mysql";
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/" + dialect + "/migration/V20261101__interview_trainer.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/" + dialect + "/migration/V20261102__interview_evaluation_run.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/" + dialect + "/migration/V20261102__interview_career.sql"));
            return null;
        });
    }
}
