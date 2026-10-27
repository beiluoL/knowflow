package com.knowflow.flashcards;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.service.LearningEventService;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FlashcardFsrsService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LearningFlashcardMapper cards;
    private final LearningEventService events;
    // Ensures additive columns exist before this service accepts requests.
    private final FlashcardSchedulingSchema schema;

    public record Settings(
            FlashcardScheduler.Options scheduling,
            int newCardLimit,
            int reviewLimit,
            String timezone,
            int dayStartHour) {}

    public record Memory(
            String algorithm, String requestId, int rating, FlashcardScheduler.Result result) {}

    @Data
    public static class ReviewInput {
        private int rating;
        private long revision;
        private long settingsRevision;
        private String requestId;
        private Long knowledgeId;
        private String sessionId;
    }

    public static Settings defaults() {
        return new Settings(FlashcardScheduler.defaults(), 20, 200, "Asia/Shanghai", 4);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize scheduling data", ex);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid persisted scheduling data", ex);
        }
    }

    public Settings settings(long uid) {
        var rows =
                jdbc.queryForList(
                        "SELECT settings_json FROM learning_flashcard_settings WHERE user_id=?",
                        uid);
        return rows.isEmpty()
                ? defaults()
                : decode((String) rows.get(0).get("settings_json"), Settings.class);
    }

    private void validate(Settings settings) {
        try {
            if (settings == null) throw new IllegalArgumentException("请填写复习设置");
            FlashcardScheduler.validate(settings.scheduling());
            if (settings.timezone() == null) throw new IllegalArgumentException("缺少时区");
            ZoneId.of(settings.timezone());
            if (settings.newCardLimit() < 0
                    || settings.newCardLimit() > 1000
                    || settings.reviewLimit() < 0
                    || settings.reviewLimit() > 10000
                    || settings.dayStartHour() < 0
                    || settings.dayStartHour() > 23)
                throw new IllegalArgumentException("每日上限或每日切换时间无效");
        } catch (IllegalArgumentException | DateTimeException ex) {
            throw new BusinessException(400, "复习设置无效：" + ex.getMessage());
        }
    }

    @Transactional
    public Settings saveSettings(long uid, Settings settings) {
        validate(settings);
        jdbc.update(
                "INSERT INTO learning_flashcard_settings(user_id,settings_json,revision)"
                    + " VALUES(?,?,1) ON DUPLICATE KEY UPDATE settings_json=?,revision=revision+1",
                uid,
                encode(settings),
                encode(settings));
        return settings;
    }

    private LearningFlashcard require(long id, long uid, boolean lock) {
        var card = lock ? cards.selectForReview(id) : cards.selectById(id);
        if (card == null) throw new BusinessException(404, "闪卡不存在");
        if (!Objects.equals(card.getUserId(), uid)) throw new BusinessException(403, "无权复习他人的闪卡");
        return card;
    }

    private long revision(long id) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT fsrs_revision FROM learning_flashcard WHERE id=?", Long.class, id));
    }

    private Memory memory(long id) {
        String raw = cards.fsrsState(id);
        return raw == null ? null : decode(raw, Memory.class);
    }

    private Instant due(LearningFlashcard card) {
        return card.getNextReviewTime() == null
                ? null
                : card.getNextReviewTime().atZone(ZoneId.systemDefault()).toInstant();
    }

    private Map<String, Object> result(Memory memory, long revision) {
        var r = memory.result();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("algorithm", memory.algorithm());
        map.put("revision", revision);
        map.put("dueAt", r.due().toString());
        map.put("state", r.state());
        map.put("stability", r.stability());
        map.put("difficulty", r.difficulty());
        map.put("intervalSeconds", r.intervalSeconds());
        map.put("rating", memory.rating());
        return map;
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> preview(long id, long uid) {
        var card = require(id, uid, false);
        var memory = memory(id);
        var settings = settings(uid);
        var now = Instant.now();
        List<Map<String, Object>> previews = new ArrayList<>();
        for (int rating = 1; rating <= 4; rating++) {
            var r =
                    FlashcardScheduler.schedule(
                            memory == null ? null : memory.result().cardJson(),
                            rating,
                            now,
                            settings.scheduling());
            previews.add(
                    Map.of(
                            "rating",
                            rating,
                            "dueAt",
                            r.due().toString(),
                            "intervalSeconds",
                            r.intervalSeconds()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        var settingsVersions =
                jdbc.queryForList(
                        "SELECT revision FROM learning_flashcard_settings WHERE user_id=?", uid);
        result.put(
                "settingsRevision",
                settingsVersions.isEmpty()
                        ? 0
                        : ((Number) settingsVersions.get(0).get("revision")).longValue());
        result.put("revision", revision(id));
        result.put("state", memory == null ? "NEW" : memory.result().state());
        result.put("algorithm", memory == null ? "LEGACY_SM2_UNCHANGED" : memory.algorithm());
        result.put("previousDueAt", due(card) == null ? null : due(card).toString());
        result.put(
                "conservativeInitialization", memory == null && card.getLastReviewTime() != null);
        result.put("previews", previews);
        result.put("serverTime", now.toString());
        return result;
    }

    private Map<String, Integer> daily(long uid, Settings settings, Instant now) {
        var zone = ZoneId.of(settings.timezone());
        var local = now.atZone(zone);
        var date = local.toLocalDate();
        if (local.getHour() < settings.dayStartHour()) date = date.minusDays(1);
        var start = date.atTime(settings.dayStartHour(), 0).atZone(zone).toInstant();
        var end = date.plusDays(1).atTime(settings.dayStartHour(), 0).atZone(zone).toInstant();
        int newCount = 0, reviewCount = 0;
        for (var row :
                jdbc.queryForList(
                        "SELECT metadata FROM learning_event WHERE user_id=? AND"
                            + " event_type='FLASHCARD_REVIEWED' AND resource_type='FLASHCARD' AND"
                            + " deleted=0 AND create_time>=? AND create_time<?",
                        uid,
                        Timestamp.from(start),
                        Timestamp.from(end))) {
            String raw = (String) row.get("metadata");
            try {
                if (raw != null && json.readTree(raw).path("startedNew").asBoolean(false))
                    newCount++;
                else reviewCount++;
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Invalid persisted learning event", ex);
            }
        }
        return Map.of(
                "newCards", newCount, "reviews", reviewCount, "completed", newCount + reviewCount);
    }

    public Map<String, Object> statistics(long uid) {
        var settings = settings(uid);
        return Map.of("settings", settings, "today", daily(uid, settings, Instant.now()));
    }

    public Map<String, Object> session(long uid, String sessionId) {
        if (!sessionId.matches("[A-Za-z0-9_-]{12,80}")) throw new BusinessException(400, "会话编号无效");
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (var row :
                jdbc.queryForList(
                        "SELECT resource_id,metadata,create_time FROM learning_event WHERE"
                            + " user_id=? AND resource_type='FLASHCARD' AND"
                            + " event_type='FLASHCARD_REVIEWED' AND deleted=0 AND metadata LIKE ?"
                            + " ORDER BY id LIMIT 10000",
                        uid,
                        "%" + sessionId + "%")) {
            try {
                var meta = json.readTree((String) row.get("metadata"));
                if (sessionId.equals(meta.path("sessionId").asText())
                        && FlashcardScheduler.VERSION.equals(meta.path("algorithm").asText()))
                    reviews.add(
                            Map.of(
                                    "cardId",
                                    row.get("resource_id"),
                                    "rating",
                                    meta.path("rating").asInt(),
                                    "reviewedAt",
                                    row.get("create_time").toString()));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Invalid persisted session event", ex);
            }
        }
        return Map.of("sessionId", sessionId, "reviews", reviews);
    }

    @Transactional
    public Map<String, Object> review(long id, long uid, ReviewInput input) {
        if (input == null
                || input.getRating() < 1
                || input.getRating() > 4
                || input.getRequestId() == null
                || !input.getRequestId().matches("[A-Za-z0-9_-]{12,80}"))
            throw new BusinessException(400, "评分或请求编号无效");
        if (input.getSessionId() != null && !input.getSessionId().matches("[A-Za-z0-9_-]{12,80}"))
            throw new BusinessException(400, "会话编号无效");
        // One user settings row serializes daily-limit checks across all their cards.
        jdbc.update(
                "INSERT INTO learning_flashcard_settings(user_id,settings_json,revision)"
                        + " VALUES(?,?,0) ON DUPLICATE KEY UPDATE user_id=user_id",
                uid,
                encode(defaults()));
        var settings =
                decode(
                        jdbc.queryForObject(
                                "SELECT settings_json FROM learning_flashcard_settings WHERE"
                                        + " user_id=? FOR UPDATE",
                                String.class,
                                uid),
                        Settings.class);
        long settingsVersion =
                jdbc.queryForObject(
                        "SELECT revision FROM learning_flashcard_settings WHERE user_id=?",
                        Long.class,
                        uid);
        var card = require(id, uid, true);
        var memory = memory(id);
        long revision = revision(id);
        // Resolve old operations from the original event log, including retries after later
        // reviews.
        for (var row :
                jdbc.queryForList(
                        "SELECT metadata FROM learning_event WHERE user_id=? AND"
                            + " event_type='FLASHCARD_REVIEWED' AND resource_type='FLASHCARD' AND"
                            + " resource_id=? AND deleted=0 AND metadata LIKE ? ORDER BY id DESC",
                        uid,
                        id,
                        "%" + input.getRequestId() + "%")) {
            try {
                var meta = json.readTree((String) row.get("metadata"));
                if (!input.getRequestId().equals(meta.path("requestId").asText())) continue;
                if (input.getRating() != meta.path("rating").asInt())
                    throw new BusinessException(409, "请求编号已用于其他评分");
                if (!meta.hasNonNull("schedulingResult"))
                    throw new BusinessException(409, "旧请求已记录，请重新加载学习进度");
                return result(
                        decode(meta.path("schedulingResult").asText(), Memory.class),
                        meta.path("revision").asLong());
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Invalid persisted review operation", ex);
            }
        }
        if (memory != null && input.getRequestId().equals(memory.requestId())) {
            if (input.getRating() != memory.rating())
                throw new BusinessException(409, "请求编号已用于其他评分");
            return result(memory, revision);
        }
        if (settingsVersion != input.getSettingsRevision())
            throw new BusinessException(409, "复习设置已更新，请刷新排程后评分");
        if (revision != input.getRevision()) throw new BusinessException(409, "复习状态已更新，请重新加载后评分");
        var now = Instant.now();
        var counts = daily(uid, settings, now);
        boolean isNew =
                card.getLastReviewTime() == null
                        && (card.getReviewCount() == null || card.getReviewCount() == 0);
        if (isNew
                ? counts.get("newCards") >= settings.newCardLimit()
                : counts.get("reviews") >= settings.reviewLimit())
            throw new BusinessException(409, "已达到今日学习上限，可在设置中调整");
        var next =
                FlashcardScheduler.schedule(
                        memory == null ? null : memory.result().cardJson(),
                        input.getRating(),
                        now,
                        settings.scheduling());
        var updated =
                new Memory(
                        FlashcardScheduler.VERSION, input.getRequestId(), input.getRating(), next);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("algorithm", FlashcardScheduler.VERSION);
        meta.put("rating", input.getRating());
        meta.put("quality", new int[] {1, 3, 4, 5}[input.getRating() - 1]);
        meta.put("lapsed", input.getRating() == 1);
        meta.put("interval", next.intervalSeconds() / 86400);
        meta.put("dueAt", next.due().toString());
        meta.put("requestId", input.getRequestId());
        meta.put("revision", revision + 1);
        meta.put("sessionId", input.getSessionId());
        meta.put("startedNew", isNew);
        meta.put("scheduler", next.schedulerJson());
        meta.put("reviewLog", next.reviewLogJson());
        meta.put("cardState", next.cardJson());
        // Keep the real pre-cutover schedule for auditable/manual scheduling rollback; never
        // synthesize old FSRS reviews.
        meta.put("previousDueAt", due(card) == null ? null : due(card).toString());
        meta.put("previousMemory", memory == null ? null : encode(memory));
        meta.put("schedulingResult", encode(updated));
        meta.put(
                "previousLastReviewTime",
                card.getLastReviewTime() == null ? null : card.getLastReviewTime().toString());
        meta.put("previousInterval", card.getReviewInterval());
        meta.put("previousReviewCount", card.getReviewCount());
        if (card.getChapterId() != null) meta.put("chapterId", card.getChapterId());
        if (input.getKnowledgeId() != null
                && jdbc.queryForObject(
                                "SELECT COUNT(*) FROM resource_knowledge_mapping WHERE"
                                        + " resource_type='FLASHCARD' AND resource_id=? AND"
                                        + " knowledge_id=? AND status='ACCEPTED' AND deleted=0",
                                Integer.class,
                                id,
                                input.getKnowledgeId())
                        > 0) meta.put("knowledgeId", input.getKnowledgeId());
        int changed =
                jdbc.update(
                        "UPDATE learning_flashcard SET"
                            + " fsrs_state=?,fsrs_revision=fsrs_revision+1,next_review_time=?,last_review_time=?,review_interval=?,review_count=CASE"
                            + " WHEN ?=1 THEN 0 ELSE COALESCE(review_count,0)+1 END,update_time=?"
                            + " WHERE id=? AND user_id=? AND deleted=0 AND fsrs_revision=?",
                        encode(updated),
                        Timestamp.from(next.due()),
                        Timestamp.from(now),
                        next.intervalSeconds() / 86400,
                        input.getRating(),
                        Timestamp.from(now),
                        id,
                        uid,
                        revision);
        if (changed != 1) throw new BusinessException(409, "复习状态已变化，请重新加载");
        events.recordRequired(uid, "FLASHCARD_REVIEWED", "FLASHCARD", id, meta);
        return result(updated, revision + 1);
    }
}
