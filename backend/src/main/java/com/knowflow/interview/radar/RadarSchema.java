package com.knowflow.interview.radar;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Only stage metadata for existing V2 sessions; no new session state or review scheduler. */
@Component @RequiredArgsConstructor
public class RadarSchema {
 private final JdbcTemplate jdbc;
 @PostConstruct void initialize(){jdbc.execute("CREATE TABLE IF NOT EXISTS interview_radar_session_binding (session_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,stage VARCHAR(20) NOT NULL,interview_mode VARCHAR(24) NOT NULL,source_review_status VARCHAR(32) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");}
}
