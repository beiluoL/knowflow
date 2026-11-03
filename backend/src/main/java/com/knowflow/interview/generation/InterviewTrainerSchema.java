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
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/" + dialect + "/migration/V20261102_2__career_workbench.sql"));
            // Career Apps V2.0：简历工具箱分析记录 + 模板设计配置（只新增表）
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/" + dialect + "/migration/V20261103__career_apps_v2.sql"));
            // Career V3.0：岗位来源溯源、搜索历史、定制任务（只新增表，不改既有表）
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/" + dialect + "/migration/V20261103_2__career_v3.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/" + dialect + "/migration/V20261103_3__career_pipeline_events.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/" + dialect + "/migration/V20261103_4__resume_studio_draft.sql"));
            // Voice Runtime：实例级语音运行模式设置（单行表，只更新不删除）
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/" + dialect + "/migration/V20261103_5__voice_runtime.sql"));
            // 兼容本轮已初始化过的开发数据库，不重建表或清空私人草稿。
            String draftTable = "h2".equals(dialect) ? "INTERVIEW_RESUME_DRAFT" : "interview_resume_draft";
            String createdColumn = "h2".equals(dialect) ? "CREATE_TIME" : "create_time";
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, draftTable, createdColumn)) {
                if (!columns.next()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE interview_resume_draft ADD COLUMN create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
                    }
                }
            }
            return null;
        });
    }
}
