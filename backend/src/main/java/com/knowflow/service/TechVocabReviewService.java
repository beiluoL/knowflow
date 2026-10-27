package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.TechVocabDtos;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.TechVocabPronunciation;
import com.knowflow.entity.TechVocabTerm;
import com.knowflow.entity.UserVocabState;
import com.knowflow.exception.BusinessException;
import com.knowflow.flashcards.FlashcardFsrsService;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.TechVocabPronunciationMapper;
import com.knowflow.mapper.TechVocabTermMapper;
import com.knowflow.mapper.UserVocabStateMapper;
import com.knowflow.vo.TechVocabVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 技术英语 P1 词汇训练与复习映射。
 *
 * 边界（严格遵守实施计划 §8）：
 * - 一个用户 + 一个词条只建立一条权威复习关系，复用 user_vocab_state.linked_flashcard_id；
 * - 复习卡是既有 learning_flashcard 的一种来源（sourceType=TECH_ENGLISH），调度、到期、评分、
 *   学习事件全部交给 FlashcardFsrsService，绝不实现第二套 SM-2 / FSRS；
 * - 收藏 ≠ 加入复习，预览 ≠ 评分；只有显式评分才写入权威复习记录；
 * - 词条文本修改不会触碰复习卡与历史（映射只依赖稳定 ID）。
 */
@Service
@RequiredArgsConstructor
public class TechVocabReviewService {

    private static final String SOURCE_TYPE = "TECH_ENGLISH";
    private static final String VOCAB_TAG = "tech-english";
    private static final int MAX_QUEUE_LIMIT = 50;

    private final TechVocabTermMapper termMapper;
    private final TechVocabPronunciationMapper pronunciationMapper;
    private final UserVocabStateMapper stateMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final FlashcardFsrsService scheduling;
    private final JdbcTemplate jdbc;

    /* ============================ 加入复习（幂等） ============================ */

    /**
     * 将词条加入复习：同一用户 + 词条只会有一条闪卡。
     * 先对 user_vocab_state 行加锁串行化，避免并发创建重复复习卡。
     */
    @Transactional
    public TechVocabVOs.AddReviewResultVO addToReview(long userId, long termId) {
        TechVocabTerm term = requirePublished(termId);
        jdbc.update(
                "INSERT INTO user_vocab_state(user_id,term_id,favorite,learning_status,deleted)"
                        + " VALUES(?,?,0,'NEW',0) ON DUPLICATE KEY UPDATE user_id=user_id",
                userId, termId);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id,linked_flashcard_id,learning_status FROM user_vocab_state"
                        + " WHERE user_id=? AND term_id=? AND deleted=0 FOR UPDATE",
                userId, termId);
        Long linked = row.get("linked_flashcard_id") == null
                ? null : ((Number) row.get("linked_flashcard_id")).longValue();
        LearningFlashcard existing = linked == null ? null : flashcardMapper.selectById(linked);
        if (existing != null && Objects.equals(existing.getUserId(), userId)) {
            markLearning(row.get("id"), userId);
            return new TechVocabVOs.AddReviewResultVO(existing.getId(), false);
        }
        LearningFlashcard card = new LearningFlashcard();
        card.setUserId(userId);
        card.setFront(term.getHeadword());
        card.setBack(term.getMeaningCn());
        card.setCategory("专业英语");
        card.setTags(VOCAB_TAG);
        card.setDifficulty(1);
        card.setSourceType(SOURCE_TYPE);
        card.setReviewCount(0);
        card.setReviewInterval(0);
        flashcardMapper.insert(card);
        jdbc.update(
                "UPDATE user_vocab_state SET linked_flashcard_id=?,learning_status=?,update_time=?"
                        + " WHERE id=? AND user_id=? AND deleted=0",
                card.getId(), "LEARNING", Timestamp.valueOf(LocalDateTime.now()),
                ((Number) row.get("id")).longValue(), userId);
        return new TechVocabVOs.AddReviewResultVO(card.getId(), true);
    }

    private void markLearning(Object stateId, long userId) {
        if (stateId == null) {
            return;
        }
        jdbc.update(
                "UPDATE user_vocab_state SET learning_status=CASE WHEN learning_status='NEW'"
                        + " THEN 'LEARNING' ELSE learning_status END,update_time=?"
                        + " WHERE id=? AND user_id=? AND deleted=0",
                Timestamp.valueOf(LocalDateTime.now()), ((Number) stateId).longValue(), userId);
    }

    /* ============================ 复习队列 ============================ */

    /**
     * 今日复习队列：已加入复习且到期/新卡在前，未加入复习的新词补充在后。
     * 队列只是展示层排序，不改变任何调度状态。
     */
    public TechVocabVOs.ReviewQueueVO queue(long userId, Integer requestedLimit) {
        int limit = requestedLimit == null ? 20 : Math.min(Math.max(requestedLimit, 1), MAX_QUEUE_LIMIT);
        Instant now = Instant.now();
        Map<Long, Map<String, Object>> states = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT s.term_id,s.linked_flashcard_id,s.favorite,s.learning_status,"
                        + " c.next_review_time,c.last_review_time,c.review_count,c.deleted AS card_deleted"
                        + " FROM user_vocab_state s LEFT JOIN learning_flashcard c"
                        + " ON c.id=s.linked_flashcard_id"
                        + " WHERE s.user_id=? AND s.deleted=0", userId)) {
            states.put(((Number) row.get("term_id")).longValue(), row);
        }

        List<Long> dueTermIds = new ArrayList<>();
        List<Long> newTermIds = new ArrayList<>();
        Map<Long, Map<String, Object>> linked = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, Object>> entry : states.entrySet()) {
            Map<String, Object> row = entry.getValue();
            Number cardId = (Number) row.get("linked_flashcard_id");
            Object cardDeleted = row.get("card_deleted");
            boolean usableCard = cardId != null
                    && (cardDeleted == null || ((Number) cardDeleted).intValue() == 0);
            if (!usableCard) {
                newTermIds.add(entry.getKey());
                continue;
            }
            Object next = row.get("next_review_time");
            boolean due = next == null
                    || ((Timestamp) next).toInstant().compareTo(now) <= 0;
            if (due) {
                dueTermIds.add(entry.getKey());
            }
            linked.put(entry.getKey(), row);
        }

        Map<Long, TechVocabTerm> terms = loadPublishedTerms(dueTermIds, newTermIds);
        List<Long> extraNew = new ArrayList<>();
        if (dueTermIds.size() + newTermIds.size() < limit) {
            extraNew = jdbc.queryForList(
                    "SELECT t.id FROM tech_vocab_term t WHERE t.content_status='PUBLISHED'"
                            + " AND t.deleted=0 AND NOT EXISTS (SELECT 1 FROM user_vocab_state s"
                            + " WHERE s.user_id=? AND s.term_id=t.id AND s.deleted=0)"
                            + " ORDER BY t.id ASC LIMIT ?", Long.class, userId,
                    limit - dueTermIds.size() - newTermIds.size());
            terms.putAll(loadPublishedTerms(extraNew, List.of()));
        }

        List<Long> ordered = new ArrayList<>(dueTermIds);
        ordered.addAll(newTermIds);
        ordered.addAll(extraNew);
        List<Long> limited = ordered.stream().filter(terms::containsKey).limit(limit)
                .collect(Collectors.toList());
        Map<Long, List<TechVocabPronunciation>> pronunciations = loadPronunciations(limited);
        List<TechVocabVOs.ReviewItemVO> items = new ArrayList<>();
        for (Long termId : limited) {
            TechVocabTerm term = terms.get(termId);
            Map<String, Object> row = states.get(termId);
            List<TechVocabPronunciation> verified = playable(
                    pronunciations.getOrDefault(termId, List.of()));
            boolean acronymMode = "ACRONYM".equals(term.getTermType())
                    && term.getFullForm() != null && !term.getFullForm().isBlank();
            items.add(new TechVocabVOs.ReviewItemVO(
                    termId,
                    row == null ? null : number(row.get("linked_flashcard_id")),
                    term.getHeadword(), term.getTermType(), term.getDomain(), term.getMeaningCn(),
                    term.getFullForm(), term.getClassName(), term.getDefinitionCn(),
                    term.getIpaUs(), term.getIpaUk(),
                    verified.stream().map(this::toPronunciationVO).collect(Collectors.toList()),
                    acronymMode, !verified.isEmpty(),
                    row == null ? "NEW" : (String) row.get("learning_status"),
                    row != null && Boolean.TRUE.equals(row.get("favorite")),
                    row == null || row.get("next_review_time") == null
                            ? null : ((Timestamp) row.get("next_review_time")).toLocalDateTime(),
                    row == null || row.get("review_count") == null
                            ? 0 : ((Number) row.get("review_count")).intValue()));
        }
        boolean hasReviewCards = states.values().stream()
                .anyMatch(r -> number(r.get("linked_flashcard_id")) != null);
        return new TechVocabVOs.ReviewQueueVO(items, dueTermIds.size(), newTermIds.size(), limit,
                hasReviewCards);
    }

    /* ============================ 权威评分 ============================ */

    /**
     * 通过既有 FSRS 服务评分：revision / settingsRevision 由服务端读取，
     * requestId 交给原有幂等协议，重复提交返回首次回执而不重复记分。
     */
    public TechVocabVOs.ReviewReceiptVO review(long userId, long termId,
                                               TechVocabDtos.ReviewInput input) {
        if (input == null || input.rating() == null || input.rating() < 1 || input.rating() > 4) {
            throw new BusinessException(400, "评分必须是 1-4");
        }
        requirePublished(termId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT linked_flashcard_id FROM user_vocab_state WHERE user_id=? AND term_id=?"
                        + " AND deleted=0", userId, termId);
        Number cardId = rows.isEmpty() ? null : (Number) rows.get(0).get("linked_flashcard_id");
        if (cardId == null) {
            throw new BusinessException(409, "该词条尚未加入复习");
        }
        LearningFlashcard card = flashcardMapper.selectById(cardId.longValue());
        if (card == null || !Objects.equals(card.getUserId(), userId)) {
            throw new BusinessException(409, "该词条尚未加入复习");
        }
        boolean replayed = requestRecorded(userId, card.getId(), input.requestId());
        Map<String, Object> preview = scheduling.preview(card.getId(), userId);
        FlashcardFsrsService.ReviewInput request = new FlashcardFsrsService.ReviewInput();
        request.setRating(input.rating());
        request.setRequestId(input.requestId());
        request.setSessionId(input.sessionId());
        request.setRevision(((Number) preview.get("revision")).longValue());
        request.setSettingsRevision(((Number) preview.get("settingsRevision")).longValue());
        Map<String, Object> result = scheduling.review(card.getId(), userId, request);
        return new TechVocabVOs.ReviewReceiptVO(termId, card.getId(), input.rating(),
                String.valueOf(result.get("dueAt")), String.valueOf(result.get("state")),
                ((Number) result.get("revision")).longValue(), replayed);
    }

    private boolean requestRecorded(long userId, long cardId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_event WHERE user_id=? AND"
                        + " event_type='FLASHCARD_REVIEWED' AND resource_type='FLASHCARD'"
                        + " AND resource_id=? AND deleted=0 AND metadata LIKE ?",
                Integer.class, userId, cardId, "%" + requestId + "%");
        return count != null && count > 0;
    }

    /* ============================ 学习看板 ============================ */

    public TechVocabVOs.VocabProgressVO progress(long userId) {
        FlashcardFsrsService.Settings settings = scheduling.settings(userId);
        ZoneId zone = ZoneId.of(settings.timezone());
        Instant now = Instant.now();
        Instant dayStart = dayStart(now, zone, settings.dayStartHour());
        Instant dayEnd = dayStart.plusSeconds(86400);
        Long totalTerms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tech_vocab_term WHERE content_status='PUBLISHED' AND deleted=0",
                Long.class);
        Long added = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_vocab_state s JOIN learning_flashcard c"
                        + " ON c.id=s.linked_flashcard_id AND c.deleted=0"
                        + " WHERE s.user_id=? AND s.deleted=0", Long.class, userId);
        Long favorite = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_vocab_state WHERE user_id=? AND favorite=1 AND deleted=0",
                Long.class, userId);
        Long learned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_vocab_state s JOIN learning_flashcard c"
                        + " ON c.id=s.linked_flashcard_id AND c.deleted=0"
                        + " WHERE s.user_id=? AND s.deleted=0 AND c.last_review_time IS NOT NULL",
                Long.class, userId);
        Long due = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_vocab_state s JOIN learning_flashcard c"
                        + " ON c.id=s.linked_flashcard_id AND c.deleted=0"
                        + " WHERE s.user_id=? AND s.deleted=0 AND c.next_review_time IS NOT NULL"
                        + " AND c.next_review_time<=?",
                Long.class, userId, Timestamp.from(now));
        Long completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_event e WHERE e.user_id=?"
                        + " AND e.event_type='FLASHCARD_REVIEWED' AND e.resource_type='FLASHCARD'"
                        + " AND e.deleted=0 AND e.create_time>=? AND e.create_time<?"
                        + " AND EXISTS (SELECT 1 FROM user_vocab_state s WHERE s.user_id=e.user_id"
                        + " AND s.linked_flashcard_id=e.resource_id AND s.deleted=0)",
                Long.class, userId, Timestamp.from(dayStart), Timestamp.from(dayEnd));
        List<TechVocabVOs.DomainProgressVO> domains = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT t.domain, COUNT(*) AS total,"
                        + " SUM(CASE WHEN c.last_review_time IS NOT NULL THEN 1 ELSE 0 END) AS learned"
                        + " FROM tech_vocab_term t LEFT JOIN user_vocab_state s"
                        + " ON s.term_id=t.id AND s.user_id=? AND s.deleted=0"
                        + " LEFT JOIN learning_flashcard c ON c.id=s.linked_flashcard_id"
                        + " AND c.deleted=0 WHERE t.content_status='PUBLISHED' AND t.deleted=0"
                        + " GROUP BY t.domain ORDER BY t.domain", userId)) {
            domains.add(new TechVocabVOs.DomainProgressVO(
                    (String) row.get("domain"),
                    ((Number) row.get("total")).longValue(),
                    ((Number) row.get("learned")).longValue()));
        }
        return new TechVocabVOs.VocabProgressVO(
                value(learned), value(due), value(completed), value(added), value(favorite),
                value(totalTerms), domains);
    }

    private Instant dayStart(Instant now, ZoneId zone, int dayStartHour) {
        var local = now.atZone(zone);
        var date = local.toLocalDate();
        if (local.getHour() < dayStartHour) {
            date = date.minusDays(1);
        }
        return date.atTime(dayStartHour, 0).atZone(zone).toInstant();
    }

    /* ============================ V3 专题学习 ============================ */

    /**
     * 专题概览：完全按现有 domain 聚合，不新增学习路径表。
     * 匿名用户传入 null，此时 learned / due 均为 0（没有个人状态，不虚构进度）。
     */
    public List<TechVocabVOs.TopicVO> topics(Long userId) {
        long uid = userId == null ? -1L : userId;
        Timestamp now = Timestamp.from(Instant.now());
        List<TechVocabVOs.TopicVO> topics = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT t.domain AS domain, COUNT(*) AS total,"
                        + " COALESCE(SUM(CASE WHEN s.learning_status IN ('LEARNING','MASTERED')"
                        + " THEN 1 ELSE 0 END),0) AS learned,"
                        + " COALESCE(SUM(CASE WHEN c.next_review_time IS NOT NULL"
                        + " AND c.next_review_time<=? THEN 1 ELSE 0 END),0) AS due"
                        + " FROM tech_vocab_term t"
                        + " LEFT JOIN user_vocab_state s ON s.term_id=t.id AND s.user_id=? AND s.deleted=0"
                        + " LEFT JOIN learning_flashcard c ON c.id=s.linked_flashcard_id AND c.deleted=0"
                        + " WHERE t.content_status='PUBLISHED' AND t.deleted=0"
                        + " GROUP BY t.domain ORDER BY t.domain", now, uid)) {
            topics.add(new TechVocabVOs.TopicVO(
                    (String) row.get("domain"),
                    ((Number) row.get("total")).longValue(),
                    ((Number) row.get("learned")).longValue(),
                    ((Number) row.get("due")).longValue()));
        }
        return topics;
    }

    /**
     * 专题内词条：按词条 id 升序，即词库编写时的「基础 → 进阶」顺序。
     * 只读取既有 FSRS 状态，不在此处写任何复习记录。
     */
    public TechVocabVOs.TopicDetailVO topic(Long userId, String domain) {
        if (domain == null || !DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new BusinessException(400, "技术方向不合法");
        }
        long uid = userId == null ? -1L : userId;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id,t.headword,t.term_type,t.meaning_cn,t.full_form,t.ipa_us,t.ipa_uk,"
                        + " s.favorite,s.learning_status,c.id AS card_id,c.next_review_time,"
                        + " c.review_count"
                        + " FROM tech_vocab_term t"
                        + " LEFT JOIN user_vocab_state s ON s.term_id=t.id AND s.user_id=? AND s.deleted=0"
                        + " LEFT JOIN learning_flashcard c ON c.id=s.linked_flashcard_id AND c.deleted=0"
                        + " WHERE t.content_status='PUBLISHED' AND t.deleted=0 AND t.domain=?"
                        + " ORDER BY t.id ASC", uid, domain);
        List<Long> ids = rows.stream().map(r -> ((Number) r.get("id")).longValue())
                .collect(Collectors.toList());
        Map<Long, List<TechVocabPronunciation>> pronunciations = loadPronunciations(ids);
        List<TechVocabVOs.TopicTermVO> terms = new ArrayList<>();
        long learned = 0;
        long due = 0;
        Timestamp now = Timestamp.from(Instant.now());
        for (Map<String, Object> row : rows) {
            long termId = ((Number) row.get("id")).longValue();
            List<TechVocabPronunciation> verified = playable(
                    pronunciations.getOrDefault(termId, List.of()));
            String status = row.get("learning_status") == null
                    ? "NEW" : (String) row.get("learning_status");
            if (!"NEW".equals(status)) {
                learned++;
            }
            Object next = row.get("next_review_time");
            boolean overdue = next instanceof Timestamp ts && !ts.after(now);
            if (overdue) {
                due++;
            }
            terms.add(new TechVocabVOs.TopicTermVO(
                    termId,
                    (String) row.get("headword"),
                    (String) row.get("term_type"),
                    (String) row.get("meaning_cn"),
                    (String) row.get("full_form"),
                    (String) row.get("ipa_us"),
                    (String) row.get("ipa_uk"),
                    verified.stream().map(this::toPronunciationVO).collect(Collectors.toList()),
                    !verified.isEmpty(),
                    status,
                    Boolean.TRUE.equals(row.get("favorite")),
                    row.get("card_id") != null,
                    overdue ? ((Timestamp) next).toLocalDateTime() : null,
                    row.get("review_count") == null ? 0 : ((Number) row.get("review_count")).intValue()));
        }
        return new TechVocabVOs.TopicDetailVO(domain, rows.size(), learned, due, terms);
    }

    /* ============================ 内部实现 ============================ */

    private static final java.util.regex.Pattern DOMAIN_PATTERN =
            java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]{1,39}");

    private TechVocabTerm requirePublished(Long termId) {
        TechVocabTerm term = termId == null ? null : termMapper.selectById(termId);
        if (term == null || !"PUBLISHED".equals(term.getContentStatus())) {
            throw new BusinessException(404, "词条不存在或未发布");
        }
        return term;
    }

    private Map<Long, TechVocabTerm> loadPublishedTerms(List<Long> first, List<Long> second) {
        List<Long> ids = new ArrayList<>(first);
        ids.addAll(second);
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return termMapper.selectBatchIds(ids.stream().distinct().collect(Collectors.toList()))
                .stream().filter(t -> "PUBLISHED".equals(t.getContentStatus()))
                .collect(Collectors.toMap(TechVocabTerm::getId, t -> t));
    }

    private Map<Long, List<TechVocabPronunciation>> loadPronunciations(List<Long> termIds) {
        if (termIds.isEmpty()) {
            return Map.of();
        }
        List<TechVocabPronunciation> rows = pronunciationMapper.selectList(
                new LambdaQueryWrapper<TechVocabPronunciation>()
                        .in(TechVocabPronunciation::getTermId, termIds)
                        .orderByAsc(TechVocabPronunciation::getId));
        Map<Long, List<TechVocabPronunciation>> map = new HashMap<>();
        for (TechVocabPronunciation row : rows) {
            map.computeIfAbsent(row.getTermId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private List<TechVocabPronunciation> playable(List<TechVocabPronunciation> rows) {
        return rows.stream()
                .filter(p -> "VERIFIED".equals(p.getVerificationStatus()))
                .filter(p -> (p.getAudioUrl() != null && !p.getAudioUrl().isBlank())
                        || (p.getReadingText() != null && !p.getReadingText().isBlank()))
                .collect(Collectors.toList());
    }

    private TechVocabVOs.PronunciationVO toPronunciationVO(TechVocabPronunciation p) {
        return new TechVocabVOs.PronunciationVO(p.getId(), p.getVariant(), p.getReadingText(),
                p.getLocale(), p.getReadingMode(), p.getAudioUrl(), p.getAudioProvider(),
                p.getLicenseNote(), p.getVerificationStatus());
    }

    private Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
