package com.knowflow.interview.radar;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** 仅维护已有 V2 会话的阶段元数据，不另建会话状态或复习调度器。 */
@Component @RequiredArgsConstructor
public class RadarSchema {
 private final JdbcTemplate jdbc;
 @PostConstruct void initialize(){jdbc.execute("CREATE TABLE IF NOT EXISTS interview_radar_session_binding (session_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,stage VARCHAR(20) NOT NULL,interview_mode VARCHAR(24) NOT NULL,source_review_status VARCHAR(32) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");}
}
