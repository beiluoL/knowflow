package com.knowflow.flashcards;

import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.LearningService;
import com.knowflow.vo.FlashcardVO;

import jakarta.annotation.PostConstruct;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.*;

/** Content notes and links only. All reviewable instances remain in learning_flashcard. */
@Service
@RequiredArgsConstructor
public class FlashcardNoteService {
    private final JdbcTemplate jdbc;
    private final LearningService learning;
    private final com.knowflow.mapper.LearningFlashcardMapper cardMapper;

    @Data
    public static class Input {
        private String type = "BASIC", front, back = "", category, tags, language = "java";
        private Integer revision;
        private boolean confirmHistoryImpact;
    }

    @PostConstruct
    void initializeAdditiveTables() {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS learning_flashcard_note (id BIGINT AUTO_INCREMENT"
                    + " PRIMARY KEY,user_id BIGINT NOT NULL,note_type VARCHAR(24) NOT NULL,front"
                    + " TEXT NOT NULL,back TEXT NOT NULL,language VARCHAR(24),category"
                    + " VARCHAR(50),tags VARCHAR(500),revision INT NOT NULL)");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS learning_flashcard_note_card (note_id BIGINT NOT"
                        + " NULL,ordinal INT NOT NULL,card_id BIGINT NOT NULL,PRIMARY"
                        + " KEY(note_id,ordinal),UNIQUE(card_id))");
    }

    private SortedMap<Integer, ClozeParser.CardText> expand(Input input) {
        if (input.getType() == null
                || !Set.of("BASIC", "CLOZE", "CODE_CLOZE", "IMAGE", "SEQUENCE", "SCENARIO")
                        .contains(input.getType())) throw new BusinessException(400, "不支持的卡片类型");
        if (input.getFront() == null
                || input.getFront().isBlank()
                || input.getFront().length() > 50_000
                || input.getBack() == null
                || input.getBack().length() > 50_000)
            throw new BusinessException(400, "请填写有效的卡片内容（最多50000字）");
        if (input.getFront().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000
                || input.getBack().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000)
            throw new BusinessException(400, "卡片内容超出存储上限");
        if (input.getCategory() != null && input.getCategory().length() > 50
                || input.getTags() != null && input.getTags().length() > 500)
            throw new BusinessException(400, "分类或标签过长");
        if (input.getLanguage() == null
                || !Set.of("java", "sql", "python", "javascript", "typescript", "text")
                        .contains(input.getLanguage()))
            throw new BusinessException(400, "不支持的代码语言");
        SortedMap<Integer, ClozeParser.CardText> cards = new TreeMap<>();
        if (input.getType().endsWith("CLOZE")) {
            try {
                cards = ClozeParser.cards(input.getFront(), input.getBack());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, e.getMessage());
            }
            if (input.getType().equals("CODE_CLOZE")) {
                var rendered = new TreeMap<Integer, ClozeParser.CardText>();
                // Fence longer than any source backtick run prevents user content from ending the
                // code block.
                String fence = "`".repeat(Math.max(3, maxBackticks(input.getFront()) + 1));
                for (var entry : cards.entrySet())
                    rendered.put(
                            entry.getKey(),
                            new ClozeParser.CardText(
                                    fence
                                            + input.getLanguage()
                                            + "\n"
                                            + entry.getValue().front()
                                            + "\n"
                                            + fence,
                                    fence
                                            + input.getLanguage()
                                            + "\n"
                                            + ClozeParser.cards(input.getFront(), "")
                                                    .get(entry.getKey())
                                                    .back()
                                            + "\n"
                                            + fence
                                            + "\n\n"
                                            + input.getBack()));
                return rendered;
            }
        } else {
            if (input.getBack().isBlank()) throw new BusinessException(400, "请填写答案");
            cards.put(0, new ClozeParser.CardText(input.getFront(), input.getBack()));
        }
        return cards;
    }

    private int maxBackticks(String s) {
        int n = 0, max = 0;
        for (char c : s.toCharArray()) {
            n = c == '`' ? n + 1 : 0;
            max = Math.max(max, n);
        }
        return max;
    }

    public Map<String, Object> preview(Input input) {
        var result = new ArrayList<Map<String, Object>>();
        expand(input)
                .forEach(
                        (ordinal, text) ->
                                result.add(
                                        Map.of(
                                                "ordinal",
                                                ordinal,
                                                "front",
                                                text.front(),
                                                "back",
                                                text.back())));
        return Map.of("cards", result);
    }

    private Map<String, Object> require(long id, long uid, boolean lock) {
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM learning_flashcard_note WHERE id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        id);
        if (rows.isEmpty()) throw new BusinessException(404, "闪卡笔记不存在");
        if (((Number) rows.get(0).get("user_id")).longValue() != uid)
            throw new BusinessException(403, "无权访问他人的闪卡笔记");
        return rows.get(0);
    }

    public Map<String, Object> get(long id, long uid) {
        var note = require(id, uid, false);
        return result(id, uid, note);
    }

    public Map<String, Object> byCard(long cardId, long uid) {
        learning.getMyFlashcard(cardId, uid);
        var rows =
                jdbc.queryForList(
                        "SELECT note_id FROM learning_flashcard_note_card WHERE card_id=?", cardId);
        return rows.isEmpty() ? null : get(((Number) rows.get(0).get("note_id")).longValue(), uid);
    }

    private Map<String, Object> result(long id, long uid, Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("type", row.get("note_type"));
        result.put("front", row.get("front"));
        result.put("back", row.get("back"));
        result.put("language", row.get("language"));
        result.put("category", row.get("category"));
        result.put("tags", row.get("tags"));
        result.put("revision", row.get("revision"));
        List<FlashcardVO> cards = new ArrayList<>();
        for (var link :
                jdbc.queryForList(
                        "SELECT card_id FROM learning_flashcard_note_card WHERE note_id=? ORDER BY"
                                + " ordinal",
                        id))
            cards.add(learning.getMyFlashcard(((Number) link.get("card_id")).longValue(), uid));
        result.put("cards", cards);
        return result;
    }

    @Transactional
    public Map<String, Object> create(long uid, Input input) {
        var cards = expand(input);
        var keys = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    var ps =
                            connection.prepareStatement(
                                    "INSERT INTO"
                                        + " learning_flashcard_note(user_id,note_type,front,back,language,category,tags,revision)"
                                        + " VALUES(?,?,?,?,?,?,?,1)",
                                    Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, uid);
                    ps.setString(2, input.getType());
                    ps.setString(3, input.getFront());
                    ps.setString(4, input.getBack());
                    ps.setString(5, input.getLanguage());
                    ps.setString(6, input.getCategory());
                    ps.setString(7, input.getTags());
                    return ps;
                },
                keys);
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        sync(id, uid, input, cards, Map.of());
        return get(id, uid);
    }

    @Transactional
    public Map<String, Object> update(long id, long uid, Input input) {
        var existing = require(id, uid, true);
        if (input.getRevision() == null
                || ((Number) existing.get("revision")).intValue() != input.getRevision())
            throw new BusinessException(409, "笔记已变化，请重新打开编辑");
        var cards = expand(input);
        Map<Integer, Long> links = new TreeMap<>();
        for (var link :
                jdbc.queryForList(
                        "SELECT ordinal,card_id FROM learning_flashcard_note_card WHERE note_id=?",
                        id))
            links.put(
                    ((Number) link.get("ordinal")).intValue(),
                    ((Number) link.get("card_id")).longValue());
        if (!cards.keySet().containsAll(links.keySet()) && !input.isConfirmHistoryImpact())
            throw new BusinessException(409, "删除或重编号将归档对应复习卡；历史事件保留，需确认历史影响");
        sync(id, uid, input, cards, links);
        jdbc.update(
                "UPDATE learning_flashcard_note SET"
                    + " note_type=?,front=?,back=?,language=?,category=?,tags=?,revision=revision+1"
                    + " WHERE id=? AND user_id=?",
                input.getType(),
                input.getFront(),
                input.getBack(),
                input.getLanguage(),
                input.getCategory(),
                input.getTags(),
                id,
                uid);
        return get(id, uid);
    }

    private void sync(
            long id,
            long uid,
            Input input,
            SortedMap<Integer, ClozeParser.CardText> cards,
            Map<Integer, Long> links) {
        for (var e : cards.entrySet()) {
            if (e.getValue().front().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            > 60000
                    || e.getValue().back().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            > 60000) throw new BusinessException(400, "生成卡片内容过长，请拆分笔记");
            var dto = new FlashcardSaveDTO();
            dto.setFront(e.getValue().front());
            dto.setBack(e.getValue().back());
            dto.setCategory(input.getCategory());
            dto.setTags(input.getTags());
            if (links.containsKey(e.getKey())) {
                long cardId = links.get(e.getKey());
                learning.getMyFlashcard(cardId, uid);
                var update =
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                                        com.knowflow.entity.LearningFlashcard>()
                                .eq(com.knowflow.entity.LearningFlashcard::getId, cardId)
                                .eq(com.knowflow.entity.LearningFlashcard::getUserId, uid)
                                .set(
                                        com.knowflow.entity.LearningFlashcard::getFront,
                                        dto.getFront())
                                .set(com.knowflow.entity.LearningFlashcard::getBack, dto.getBack())
                                .set(
                                        com.knowflow.entity.LearningFlashcard::getCategory,
                                        input.getCategory() == null ? "" : input.getCategory())
                                .set(
                                        com.knowflow.entity.LearningFlashcard::getTags,
                                        input.getTags() == null ? "" : input.getTags())
                                .set(
                                        com.knowflow.entity.LearningFlashcard::getUpdateTime,
                                        java.time.LocalDateTime.now());
                if (cardMapper.update(null, update) != 1)
                    throw new BusinessException(409, "复习卡已变化，请重新加载");
            } else {
                var card = learning.createMyFlashcard(uid, dto);
                jdbc.update(
                        "INSERT INTO learning_flashcard_note_card(note_id,ordinal,card_id)"
                                + " VALUES(?,?,?)",
                        id,
                        e.getKey(),
                        card.getId());
            }
        }
        for (var e : links.entrySet())
            if (!cards.containsKey(e.getKey())) {
                jdbc.update(
                        "DELETE FROM learning_flashcard_note_card WHERE note_id=? AND ordinal=?",
                        id,
                        e.getKey());
                learning.deleteMyFlashcard(e.getValue(), uid);
            }
    }
}
