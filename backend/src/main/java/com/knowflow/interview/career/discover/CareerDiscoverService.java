package com.knowflow.interview.career.discover;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.career.CareerRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位发现的落库能力：搜索历史 + 一键加入岗位中心。
 *
 * <p>加入岗位严格复用 {@link CareerRecordService}，不新建 Job 表、不复制 JD 解析器；
 * 来源溯源写入独立表，便于回链、去重与「来源失效」提示。</p>
 */
@Service
@RequiredArgsConstructor
public class CareerDiscoverService {

    private static final int MAX_HISTORY = 30;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CareerRecordService records;
    private final JobSourceRegistry registry;

    /* ------------------------------ 搜索历史 ------------------------------ */

    public List<Map<String, Object>> history(Long userId) {
        requireUser(userId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (var row : jdbc.queryForList("""
                SELECT id,keyword,filters_json,saved,last_used_at,create_time
                  FROM interview_career_search_history
                 WHERE user_id=? AND deleted=0
                 ORDER BY saved DESC, last_used_at DESC, id DESC LIMIT ?
                """, userId, MAX_HISTORY)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("keyword", row.get("keyword"));
            item.put("filters", parse(String.valueOf(row.get("filters_json"))));
            item.put("saved", ((Number) row.get("saved")).intValue() == 1);
            item.put("lastUsedAt", String.valueOf(row.get("last_used_at")));
            list.add(item);
        }
        return list;
    }

    @Transactional
    public Map<String, Object> addHistory(Long userId, String keyword, Map<String, Object> filters, boolean saved) {
        requireUser(userId);
        String value = keyword == null ? "" : keyword.trim();
        if (value.length() > 100) throw new BusinessException(400, "搜索关键词最多100字");
        String filtersJson = write(filters == null ? Map.of() : filters);

        var existing = jdbc.queryForList("""
                SELECT id FROM interview_career_search_history
                 WHERE user_id=? AND keyword=? AND filters_json=? AND deleted=0
                """, userId, value, filtersJson);
        if (!existing.isEmpty()) {
            long id = ((Number) existing.get(0).get("id")).longValue();
            jdbc.update("""
                    UPDATE interview_career_search_history
                       SET last_used_at=CURRENT_TIMESTAMP, saved=?, update_time=CURRENT_TIMESTAMP
                     WHERE id=? AND user_id=?
                    """, saved ? 1 : 0, id, userId);
            return Map.of("id", id, "saved", saved);
        }

        jdbc.update("""
                INSERT INTO interview_career_search_history(user_id,keyword,filters_json,saved)
                VALUES (?,?,?,?)
                """, userId, value, filtersJson, saved ? 1 : 0);
        Long id = jdbc.queryForObject("""
                SELECT id FROM interview_career_search_history
                 WHERE user_id=? AND keyword=? AND filters_json=? AND deleted=0 ORDER BY id DESC LIMIT 1
                """, Long.class, userId, value, filtersJson);

        // 只保留最近 30 条未收藏记录，超出部分逻辑删除，避免无限增长
        jdbc.update("""
                UPDATE interview_career_search_history SET deleted=1
                 WHERE user_id=? AND saved=0 AND deleted=0
                   AND id NOT IN (SELECT id FROM (
                        SELECT id FROM interview_career_search_history
                         WHERE user_id=? AND saved=0 AND deleted=0
                         ORDER BY last_used_at DESC, id DESC LIMIT ?
                   ) t)
                """, userId, userId, MAX_HISTORY);
        return Map.of("id", id == null ? 0 : id, "saved", saved);
    }

    @Transactional
    public void removeHistory(Long userId, long id) {
        requireUser(userId);
        jdbc.update("""
                UPDATE interview_career_search_history SET deleted=1, update_time=CURRENT_TIMESTAMP
                 WHERE id=? AND user_id=? AND deleted=0
                """, id, userId);
    }

    /* ------------------------------ 加入岗位中心 ------------------------------ */

    public record ImportRequest(
            String sourceId,
            String externalId,
            String title,
            String company,
            String location,
            String description,
            String sourceUrl,
            String applicationUrl,
            String postedAt,
            String attribution
    ) { }

    /**
     * 把外部岗位加入本人岗位中心。
     *
     * <p>幂等：同一用户 + 同一来源 + 同一外部 ID（或同 URL）只创建一条岗位记录。
     * 原文按不可信数据处理，只存储、不执行其中任何指令。</p>
     */
    @Transactional
    public Map<String, Object> importJob(Long userId, ImportRequest request) {
        requireUser(userId);
        if (request == null) throw new BusinessException(400, "缺少岗位信息");
        if (request.sourceId() == null || registry.get(request.sourceId()) == null) {
            throw new BusinessException(400, "岗位来源未注册");
        }
        String title = trim(request.title(), 160, "岗位标题");
        String url = optionalUrl(request.sourceUrl());
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() > 20000) throw new BusinessException(400, "岗位描述最多20000字");
        if (description.isBlank()) {
            description = title + (request.company() == null ? "" : "\n" + request.company().trim());
        }

        long existing = findImported(userId, request.sourceId(), request.externalId(), url);
        if (existing > 0) {
            return Map.of("recordId", existing, "duplicate", true);
        }

        var record = records.create(userId, "JOB",
                new CareerRecordService.Edit(null, title, description, Map.of()));
        long recordId = ((Number) record.get("id")).longValue();
        jdbc.update("""
                INSERT INTO interview_career_job_source
                    (user_id,record_id,source_id,source_name,external_job_id,source_url,attribution,posted_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, userId, recordId, request.sourceId(),
                registry.get(request.sourceId()).displayName(),
                trimOptional(request.externalId(), 191), url, trimOptional(request.attribution(), 200),
                parseTimestamp(request.postedAt()));
        return Map.of("recordId", recordId, "duplicate", false);
    }

    public Map<String, Object> jobSource(Long userId, long recordId) {
        requireUser(userId);
        var rows = jdbc.queryForList("""
                SELECT source_id,source_name,external_job_id,source_url,attribution,posted_at,fetched_at
                  FROM interview_career_job_source WHERE user_id=? AND record_id=?
                """, userId, recordId);
        if (rows.isEmpty()) return Map.of();
        var row = rows.get(0);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceId", row.get("source_id"));
        item.put("sourceName", row.get("source_name"));
        item.put("externalId", row.get("external_job_id"));
        item.put("sourceUrl", row.get("source_url"));
        item.put("attribution", row.get("attribution"));
        item.put("postedAt", row.get("posted_at"));
        item.put("fetchedAt", row.get("fetched_at"));
        return item;
    }

    private long findImported(Long userId, String sourceId, String externalId, String url) {
        if (externalId != null && !externalId.isBlank()) {
            var rows = jdbc.queryForList("""
                    SELECT record_id FROM interview_career_job_source
                     WHERE user_id=? AND source_id=? AND external_job_id=? LIMIT 1
                    """, userId, sourceId, externalId.trim());
            if (!rows.isEmpty()) return ((Number) rows.get(0).get("record_id")).longValue();
        }
        if (url != null) {
            var rows = jdbc.queryForList("""
                    SELECT record_id FROM interview_career_job_source
                     WHERE user_id=? AND source_url=? LIMIT 1
                    """, userId, url);
            if (!rows.isEmpty()) return ((Number) rows.get(0).get("record_id")).longValue();
        }
        return 0L;
    }

    private String trim(String value, int max, String label) {
        if (value == null || value.isBlank()) throw new BusinessException(400, "缺少" + label);
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new BusinessException(400, label + "最多" + max + "字");
        return trimmed;
    }

    private String trimOptional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    /** 只接受 http/https 且长度受限；不做域名白名单（用户自己选择要导入的岗位）。 */
    private String optionalUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 2048) throw new BusinessException(400, "岗位链接最多2048字符");
        try {
            var uri = java.net.URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new BusinessException(400, "岗位链接只支持 http 或 https");
            }
            if (uri.getHost() == null) throw new BusinessException(400, "岗位链接缺少域名");
            return trimmed;
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(400, "岗位链接格式无效");
        }
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Timestamp.from(Instant.parse(value.trim()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String write(Map<String, Object> filters) {
        try {
            String value = json.writeValueAsString(filters);
            return value.length() > 2000 ? "{}" : value;
        } catch (Exception invalid) {
            return "{}";
        }
    }

    private Map<String, Object> parse(String raw) {
        try {
            return json.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception invalid) {
            return Map.of();
        }
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
    }
}
