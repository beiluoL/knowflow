package com.knowflow.interview.career;

import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 岗位投递状态仅属于本人；不参与任何授权判断，也不改写 JD 版本。 */
@Service
@RequiredArgsConstructor
public class CareerJobPipelineService {

    public static final Set<String> STATUSES = Set.of(
            "SAVED", "TO_APPLY", "APPLIED", "WRITTEN_TEST",
            "INTERVIEWING", "OFFER", "REJECTED", "CLOSED");

    private final JdbcTemplate jdbc;
    private final CareerRecordService records;

    public Map<String,Object> get(Long userId, long recordId) {
        var record = records.detail(userId, recordId, null);
        if (!"JOB".equals(record.get("kind"))) {
            throw new BusinessException(400, "仅岗位可以设置投递状态");
        }
        return view(userId, recordId);
    }

    @Transactional
    public Map<String,Object> update(Long userId, long recordId, String status, String note) {
        if (status == null || !STATUSES.contains(status)) {
            throw new BusinessException(400, "岗位状态无效");
        }
        if (note != null && note.length() > 2000) {
            throw new BusinessException(400, "投递备注最多2000字");
        }
        var record = records.detail(userId, recordId, null);
        if (!"JOB".equals(record.get("kind"))) {
            throw new BusinessException(400, "仅岗位可以设置投递状态");
        }
        // 锁住岗位身份，使首次创建状态行与后续事件写入使用同一串行事务。
        jdbc.queryForList("SELECT id FROM interview_career_record WHERE id=? AND user_id=? AND deleted=0 FOR UPDATE", recordId, userId);
        String previousStatus = String.valueOf(view(userId, recordId).get("status"));
        Timestamp appliedAt = Set.of("APPLIED", "WRITTEN_TEST", "INTERVIEWING", "OFFER", "REJECTED")
                .contains(status) ? Timestamp.valueOf(java.time.LocalDateTime.now()) : null;
        int updated = jdbc.update("""
                UPDATE interview_career_job_pipeline
                   SET status=?, note=?, applied_at=COALESCE(applied_at, ?), update_time=CURRENT_TIMESTAMP
                 WHERE record_id=? AND user_id=?
                """, status, note, appliedAt, recordId, userId);
        if (updated == 0) {
            try {
                jdbc.update("""
                        INSERT INTO interview_career_job_pipeline(record_id,user_id,status,note,applied_at)
                        VALUES(?,?,?,?,?)
                        """, recordId, userId, status, note, appliedAt);
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                jdbc.update("""
                        UPDATE interview_career_job_pipeline
                           SET status=?, note=?, applied_at=COALESCE(applied_at, ?), update_time=CURRENT_TIMESTAMP
                         WHERE record_id=? AND user_id=?
                        """, status, note, appliedAt, recordId, userId);
            }
        }
        if (!status.equals(previousStatus)) {
            jdbc.update("""
                    INSERT INTO interview_career_job_event(record_id,user_id,event_type,from_status,to_status,note)
                    VALUES(?,?,?,?,?,?)
                    """, recordId, userId, "STATUS_CHANGED", previousStatus, status, note == null ? "" : note);
        }
        return view(userId, recordId);
    }

    public List<Map<String,Object>> events(Long userId, long recordId) {
        get(userId, recordId);
        return jdbc.query("""
                SELECT id,event_type,from_status,to_status,note,occurred_at
                  FROM interview_career_job_event WHERE record_id=? AND user_id=? ORDER BY id DESC LIMIT 200
                """, (rs, index) -> {
                    Map<String,Object> event = new LinkedHashMap<>();
                    event.put("id", rs.getLong("id"));
                    event.put("event_type", rs.getString("event_type"));
                    event.put("from_status", rs.getString("from_status"));
                    event.put("to_status", rs.getString("to_status"));
                    event.put("note", rs.getString("note"));
                    event.put("occurred_at", rs.getTimestamp("occurred_at"));
                    return event;
                }, recordId, userId);
    }

    /** 缺失记录视为「关注中」，不把未操作状态伪装成已投递。 */
    public Map<String,Object> view(Long userId, long recordId) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT status,note,applied_at,update_time
                  FROM interview_career_job_pipeline
                 WHERE record_id=? AND user_id=?
                """, recordId, userId);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("recordId", recordId);
        if (rows.isEmpty()) {
            result.put("status", "SAVED");
            result.put("note", "");
            result.put("appliedAt", null);
            result.put("updatedAt", null);
        } else {
            var row = rows.get(0);
            result.put("status", row.get("status"));
            result.put("note", row.get("note") == null ? "" : row.get("note"));
            result.put("appliedAt", row.get("applied_at"));
            result.put("updatedAt", row.get("update_time"));
        }
        return result;
    }

    /** 批量补全岗位状态，供列表与详情复用；避免前端自行拼装默认值。 */
    public Map<Long,Map<String,Object>> views(Long userId, List<Map<String,Object>> jobRecords) {
        var result = new LinkedHashMap<Long,Map<String,Object>>();
        for (var record : jobRecords) {
            long id = ((Number) record.get("id")).longValue();
            result.put(id, view(userId, id));
        }
        return result;
    }
}
