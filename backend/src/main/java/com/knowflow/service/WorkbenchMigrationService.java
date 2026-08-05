package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.knowflow.entity.WbCapture;
import com.knowflow.entity.WbNote;
import com.knowflow.entity.WbPalace;
import com.knowflow.entity.WbPalaceLoci;
import com.knowflow.entity.WbRecallSession;
import com.knowflow.entity.WbReviewCard;
import com.knowflow.entity.WbReviewLog;
import com.knowflow.entity.WbStory;
import com.knowflow.mapper.WbCaptureMapper;
import com.knowflow.mapper.WbNoteMapper;
import com.knowflow.mapper.WbPalaceLociMapper;
import com.knowflow.mapper.WbPalaceMapper;
import com.knowflow.mapper.WbRecallSessionMapper;
import com.knowflow.mapper.WbReviewCardMapper;
import com.knowflow.mapper.WbReviewLogMapper;
import com.knowflow.mapper.WbStoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库工作台数据迁移：将当前用户的全部工作台数据导出为与桌面端（Tauri + SQLite）契约对齐的 JSON。
 *
 * <p>契约说明（桌面端 {@code POST /api/workbench/import} 消费此结构）：
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "source": "knowflow-web",
 *   "exportedAt": "2026-08-06T03:39:56",
 *   "userId": 1,
 *   "data": {
 *     "captures":       [ {id,title,content,sourceType,sourceUrl,docId,categoryId,tags,status,starred,createTime,updateTime} ],
 *     "notes":          [ {id,captureId,categoryId,title,cueColumn,noteColumn,summaryColumn,tags,mastery,createTime,updateTime} ],
 *     "reviewCards":    [ {id,captureId,noteId,categoryId,front,back,cardType,easeFactor,repetitions,intervalDay,reviewCount,lapseCount,nextReviewTime,lastReviewTime,suspended} ],
 *     "reviewLogs":     [ {id,cardId,quality,intervalDay,easeFactor,costMs,createTime} ],
 *     "palaces":        [ {id,name,description,theme,coverColor,categoryId,createTime,updateTime} ],
 *     "palaceLoci":     [ {id,palaceId,captureId,noteId,categoryId,name,knowledgePoint,imageHint,icon,posX,posY,sortOrder} ],
 *     "recallSessions": [ {id,noteId,cardId,title,sourceText,round1Text,round1Score,round2Text,round2Score,round3Text,round3Score,currentRound,status,round3DueTime,completedTime,createTime,updateTime} ],
 *     "stories":        [ {id,captureId,noteId,categoryId,title,audience,metaphor,content,gapNote,status,clarityScore,wordCount,createTime,updateTime} ]
 *   }
 * }
 * </pre>
 *
 * <p>注意：所有时间字段统一序列化为 ISO-8601 字符串（避免 Jackson 对 LocalDateTime 的数组化），
 * 内部 {@code id} 与关联 {@code *Id} 保留 Web 端原始 Long 值，供桌面端重映射本地自增 id。
 * 关联 {@code doc_category} 的 {@code categoryId} 在桌面端无对应分类体系，导入时置空。
 */
@Service
@RequiredArgsConstructor
public class WorkbenchMigrationService {

    private final WbCaptureMapper captureMapper;
    private final WbNoteMapper noteMapper;
    private final WbReviewCardMapper reviewCardMapper;
    private final WbReviewLogMapper reviewLogMapper;
    private final WbPalaceMapper palaceMapper;
    private final WbPalaceLociMapper palaceLociMapper;
    private final WbRecallSessionMapper recallSessionMapper;
    private final WbStoryMapper storyMapper;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static String iso(LocalDateTime t) {
        return t == null ? null : t.format(ISO);
    }

    @SafeVarargs
    private static Map<String, Object> row(Map.Entry<String, Object>... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : kv) {
            m.put(e.getKey(), e.getValue());
        }
        return m;
    }

    private static Map.Entry<String, Object> k(String key, Object value) {
        return new AbstractMapEntry(key, value);
    }

    private static final class AbstractMapEntry implements Map.Entry<String, Object> {
        private final String key;
        private final Object value;

        AbstractMapEntry(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Object setValue(Object value) {
            throw new UnsupportedOperationException();
        }
    }

    private static <T> QueryWrapper<T> byUser(Long userId) {
        return new QueryWrapper<T>().eq("user_id", userId);
    }

    /** 导出当前用户全部工作台数据。 */
    public Map<String, Object> exportAll(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("captures", exportCaptures(userId));
        data.put("notes", exportNotes(userId));
        data.put("reviewCards", exportReviewCards(userId));
        data.put("reviewLogs", exportReviewLogs(userId));
        data.put("palaces", exportPalaces(userId));
        data.put("palaceLoci", exportPalaceLoci(userId));
        data.put("recallSessions", exportRecallSessions(userId));
        data.put("stories", exportStories(userId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("source", "knowflow-web");
        payload.put("exportedAt", LocalDateTime.now().format(ISO));
        payload.put("userId", userId);
        payload.put("data", data);
        return payload;
    }

    private List<Map<String, Object>> exportCaptures(Long userId) {
        List<WbCapture> list = captureMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbCapture e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("title", e.getTitle()),
                    k("content", e.getContent()),
                    k("sourceType", e.getSourceType()),
                    k("sourceUrl", e.getSourceUrl()),
                    k("docId", e.getDocId()),
                    k("categoryId", e.getCategoryId()),
                    k("tags", e.getTags()),
                    k("status", e.getStatus()),
                    k("starred", e.getStarred()),
                    k("createTime", iso(e.getCreateTime())),
                    k("updateTime", iso(e.getUpdateTime()))
            ));
        }
        return out;
    }

    private List<Map<String, Object>> exportNotes(Long userId) {
        List<WbNote> list = noteMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbNote e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("captureId", e.getCaptureId()),
                    k("categoryId", e.getCategoryId()),
                    k("title", e.getTitle()),
                    k("cueColumn", e.getCueColumn()),
                    k("noteColumn", e.getNoteColumn()),
                    k("summaryColumn", e.getSummaryColumn()),
                    k("tags", e.getTags()),
                    k("mastery", e.getMastery()),
                    k("createTime", iso(e.getCreateTime())),
                    k("updateTime", iso(e.getUpdateTime()))
            ));
        }
        return out;
    }

    private List<Map<String, Object>> exportReviewCards(Long userId) {
        List<WbReviewCard> list = reviewCardMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbReviewCard e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("captureId", e.getCaptureId()),
                    k("noteId", e.getNoteId()),
                    k("categoryId", e.getCategoryId()),
                    k("front", e.getFront()),
                    k("back", e.getBack()),
                    k("cardType", e.getCardType()),
                    k("easeFactor", e.getEaseFactor()),
                    k("repetitions", e.getRepetitions()),
                    k("intervalDay", e.getIntervalDay()),
                    k("reviewCount", e.getReviewCount()),
                    k("lapseCount", e.getLapseCount()),
                    k("nextReviewTime", iso(e.getNextReviewTime())),
                    k("lastReviewTime", iso(e.getLastReviewTime())),
                    k("suspended", e.getSuspended())
            ));
        }
        return out;
    }

    private List<Map<String, Object>> exportReviewLogs(Long userId) {
        List<WbReviewLog> list = reviewLogMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbReviewLog e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("cardId", e.getCardId()),
                    k("quality", e.getQuality()),
                    k("intervalDay", e.getIntervalDay()),
                    k("easeFactor", e.getEaseFactor()),
                    k("costMs", e.getCostMs()),
                    k("createTime", iso(e.getCreateTime()))
            ));
        }
        return out;
    }

    private List<Map<String, Object>> exportPalaces(Long userId) {
        List<WbPalace> list = palaceMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbPalace e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("name", e.getName()),
                    k("description", e.getDescription()),
                    k("theme", e.getTheme()),
                    k("coverColor", e.getCoverColor()),
                    k("categoryId", e.getCategoryId()),
                    k("createTime", iso(e.getCreateTime())),
                    k("updateTime", iso(e.getUpdateTime()))
            ));
        }
        return out;
    }

    private List<Map<String, Object>> exportPalaceLoci(Long userId) {
        List<WbPalaceLoci> list = palaceLociMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbPalaceLoci e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("palaceId", e.getPalaceId()),
                    k("captureId", e.getCaptureId()),
                    k("noteId", e.getNoteId()),
                    k("categoryId", e.getCategoryId()),
                    k("name", e.getName()),
                    k("knowledgePoint", e.getKnowledgePoint()),
                    k("imageHint", e.getImageHint()),
                    k("icon", e.getIcon()),
                    k("posX", e.getPosX()),
                    k("posY", e.getPosY()),
                    k("sortOrder", e.getSortOrder())
            ));
        }
        return out;
    }

    private List<Map<String, Object>> exportRecallSessions(Long userId) {
        List<WbRecallSession> list = recallSessionMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbRecallSession e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("noteId", e.getNoteId()),
                    k("cardId", e.getCardId()),
                    k("title", e.getTitle()),
                    k("sourceText", e.getSourceText()),
                    k("round1Text", e.getRound1Text()),
                    k("round1Score", e.getRound1Score()),
                    k("round2Text", e.getRound2Text()),
                    k("round2Score", e.getRound2Score()),
                    k("round3Text", e.getRound3Text()),
                    k("round3Score", e.getRound3Score()),
                    k("currentRound", e.getCurrentRound()),
                    k("status", e.getStatus()),
                    k("round3DueTime", iso(e.getRound3DueTime())),
                    k("completedTime", iso(e.getCompletedTime())),
                    k("createTime", iso(e.getCreateTime())),
                    k("updateTime", iso(e.getUpdateTime()))
            ));
        }
        return out;
    }

    private List<Map<String, Object>> exportStories(Long userId) {
        List<WbStory> list = storyMapper.selectList(byUser(userId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (WbStory e : list) {
            out.add(row(
                    k("id", e.getId()),
                    k("captureId", e.getCaptureId()),
                    k("noteId", e.getNoteId()),
                    k("categoryId", e.getCategoryId()),
                    k("title", e.getTitle()),
                    k("audience", e.getAudience()),
                    k("metaphor", e.getMetaphor()),
                    k("content", e.getContent()),
                    k("gapNote", e.getGapNote()),
                    k("status", e.getStatus()),
                    k("clarityScore", e.getClarityScore()),
                    k("wordCount", e.getWordCount()),
                    k("createTime", iso(e.getCreateTime())),
                    k("updateTime", iso(e.getUpdateTime()))
            ));
        }
        return out;
    }
}
