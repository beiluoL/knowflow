package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.PageResult;
import com.knowflow.dto.TechVocabDtos;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.KgEntity;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningNote;
import com.knowflow.entity.TechVocabConceptLink;
import com.knowflow.entity.TechVocabPronunciation;
import com.knowflow.entity.TechVocabTerm;
import com.knowflow.entity.UserVocabState;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningNoteMapper;
import com.knowflow.mapper.TechVocabConceptLinkMapper;
import com.knowflow.mapper.TechVocabPronunciationMapper;
import com.knowflow.mapper.TechVocabTermMapper;
import com.knowflow.mapper.UserVocabStateMapper;
import com.knowflow.vo.TechVocabVOs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 技术英语 P0 核心服务：检索、详情、发音、用户状态、管理端 CRUD 与导入。
 *
 * 边界：
 * - 只读词库默认仅返回 PUBLISHED；
 * - 用户 ID 一律由 Controller 从认证上下文传入；
 * - 不复制闪卡/SM-2/FSRS 调度，user_vocab_state.linked_flashcard_id 仅预留；
 * - 发音没有授权录音时如实返回 audioUrl = null，由前端决定 TTS 降级或提示不可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechVocabService {

    private static final Set<String> TERM_TYPES = Set.of("WORD", "ACRONYM", "PHRASE");
    private static final Set<String> CONTENT_STATUSES = Set.of("DRAFT", "REVIEWED", "PUBLISHED", "ARCHIVED");
    private static final Set<String> VARIANTS = Set.of("WORD_US", "WORD_UK", "ABBR", "FULL_FORM");
    private static final Set<String> READING_MODES = Set.of("LETTER_BY_LETTER", "SPOKEN_WORD", "MIXED");
    private static final Set<String> VERIFY_STATUSES = Set.of("VERIFIED", "PENDING", "UNAVAILABLE");
    private static final Set<String> LEARNING_STATUSES = Set.of("NEW", "LEARNING", "MASTERED");
    private static final Set<String> LINK_TYPES = Set.of("KNOWLEDGE", "CHAPTER", "DOCUMENT", "NOTE", "TERM");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");
    private static final Pattern SENSE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,59}");

    private final TechVocabTermMapper termMapper;
    private final TechVocabPronunciationMapper pronunciationMapper;
    private final UserVocabStateMapper stateMapper;
    private final TechVocabConceptLinkMapper linkMapper;
    private final DocDocumentMapper docMapper;
    private final LearningChapterMapper chapterMapper;
    private final KgEntityMapper kgEntityMapper;
    private final LearningNoteMapper noteMapper;
    private final ObjectMapper objectMapper;
    /** 关联知识可见性必须与知识库阅读权限一致，不能只看文档发布状态 */
    private final com.knowflow.security.DocumentAccessPolicy docAccess;
    /** 仅用于替换发音子行：发音是词条子内容，物理删除可避免逻辑删除值累积触发唯一键冲突 */
    private final JdbcTemplate jdbc;

    /** 导入预览短期缓存：单实例内存，10 分钟过期；确认后保留幂等回执。 */
    private final Map<String, PreviewEntry> previews = new ConcurrentHashMap<>();
    private static final long PREVIEW_TTL_MINUTES = 10L;

    /* ============================ 公共检索 ============================ */

    public PageResult<TechVocabVOs.TermSummary> search(String q, String type, String domain,
                                                       int pageNum, int pageSize) {
        Page<TechVocabTerm> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<TechVocabTerm> wrapper = publishedWrapper();
        applyType(wrapper, type);
        if (domain != null && !domain.isBlank()) {
            wrapper.eq(TechVocabTerm::getDomain, domain.trim().toUpperCase(Locale.ROOT));
        }
        applyKeyword(wrapper, q);
        wrapper.orderByAsc(TechVocabTerm::getHeadword).orderByAsc(TechVocabTerm::getId);
        Page<TechVocabTerm> result = termMapper.selectPage(page, wrapper);
        return summaryPage(result);
    }

    public TechVocabVOs.TermDetail detail(Long id, Long userId) {
        TechVocabTerm term = requirePublished(id);
        return toDetail(term, userId, false);
    }

    /** 管理端检索：包含草稿与归档，按更新时间倒序便于审核 */
    public PageResult<TechVocabVOs.TermSummary> adminSearch(String q, String type, String domain,
                                                            int pageNum, int pageSize) {
        Page<TechVocabTerm> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<TechVocabTerm> wrapper = new LambdaQueryWrapper<>();
        applyType(wrapper, type);
        if (domain != null && !domain.isBlank()) {
            wrapper.eq(TechVocabTerm::getDomain, domain.trim().toUpperCase(Locale.ROOT));
        }
        applyKeyword(wrapper, q);
        wrapper.orderByDesc(TechVocabTerm::getUpdateTime).orderByDesc(TechVocabTerm::getId);
        return summaryPage(termMapper.selectPage(page, wrapper));
    }

    public List<TechVocabVOs.PronunciationVO> pronunciations(Long id) {
        requirePublished(id);
        return loadPronunciations(List.of(id)).getOrDefault(id, List.of()).stream()
                .map(this::toPronunciationVO)
                .collect(Collectors.toList());
    }

    public TechVocabVOs.FiltersVO filters(Long userId) {
        List<TechVocabTerm> terms = termMapper.selectList(publishedWrapper()
                .select(TechVocabTerm::getId, TechVocabTerm::getTermType, TechVocabTerm::getDomain));
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String t : List.of("WORD", "ACRONYM", "PHRASE")) {
            byType.put(t, terms.stream().filter(x -> t.equals(x.getTermType())).count());
        }
        Map<String, Long> byDomain = terms.stream()
                .collect(Collectors.groupingBy(TechVocabTerm::getDomain,
                        LinkedHashMap::new, Collectors.counting()));
        List<TechVocabVOs.DomainCount> domains = byDomain.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TechVocabVOs.DomainCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        Long favoriteCount = null;
        Long learningCount = null;
        if (userId != null) {
            favoriteCount = stateMapper.selectCount(new LambdaQueryWrapper<UserVocabState>()
                    .eq(UserVocabState::getUserId, userId)
                    .eq(UserVocabState::getFavorite, true));
            learningCount = stateMapper.selectCount(new LambdaQueryWrapper<UserVocabState>()
                    .eq(UserVocabState::getUserId, userId)
                    .in(UserVocabState::getLearningStatus, "LEARNING", "MASTERED"));
        }
        return new TechVocabVOs.FiltersVO(terms.size(), byType, domains, favoriteCount, learningCount);
    }

    /* ============================ P2 划词查询 ============================ */

    private static final int LOOKUP_MAX_LENGTH = 80;
    private static final int LOOKUP_MAX_MATCHES = 5;
    /** 选区中的英文片段：允许技术词常见的 + # . _ / - 连接符 */
    private static final Pattern LATIN_RUN = Pattern.compile("[A-Za-z][A-Za-z0-9+#._/-]*");
    private static final Pattern EDGE_QUOTES = Pattern.compile(
            "^[\"'“”‘’()\\[\\]{}<>《》【】]+|[\"'“”‘’()\\[\\]{}<>《》【】]+$");
    private static final Pattern EDGE_PUNCT = Pattern.compile(
            "^[,.;:!?，。；：！？、\\-—]+|[,.;:!?，。；：！？、\\-—]+$");
    private static final Pattern CJK_CHAR = Pattern.compile("[\\u4e00-\\u9fa5]");

    /**
     * 划词查询：把用户选中的文本归一化后与已发布词库匹配。
     *
     * 约束：
     * - 只读取选中文本本身，不接收也不回传整篇文档，避免为查一个词上传私人内容；
     * - 阈值：超过 80 字符或包含换行（跨段落）时不查询，由前端隐藏入口；
     * - 同一归一化词形存在多个义项时返回多条候选，不替用户猜含义；
     * - 关联知识的可见性复用 {@link #resolveTarget} 的二次鉴权。
     */
    public TechVocabVOs.LookupResultVO lookup(String rawQuery, String contextDomain, Long userId) {
        String original = rawQuery == null ? "" : rawQuery;
        String cleaned = cleanSelection(original);
        if (cleaned == null) {
            return new TechVocabVOs.LookupResultVO(original.trim(), null,
                    "选区过长或跨段落，暂不支持划词查询", false, List.of());
        }
        if (cleaned.isEmpty()) {
            return new TechVocabVOs.LookupResultVO(original.trim(), null,
                    "选区未包含可查询的英文术语", false, List.of());
        }
        String key = normalizeLookupKey(cleaned);
        String domain = contextDomain == null ? null : contextDomain.trim().toUpperCase(Locale.ROOT);
        List<TechVocabTerm> published = termMapper.selectList(publishedWrapper());
        List<LookupScore> ranked = new ArrayList<>();
        for (TechVocabTerm term : published) {
            int score = matchScore(term, key);
            if (score <= 0) {
                continue;
            }
            if (domain != null && domain.equals(term.getDomain())) {
                // 文档技术分类优先：不确定时仍保留其他义项，由用户自行选择
                score += 12;
            }
            ranked.add(new LookupScore(term, score));
        }
        ranked.sort(Comparator.comparingInt(LookupScore::score).reversed()
                .thenComparingInt(s -> s.term().getHeadword().length())
                .thenComparingLong(s -> s.term().getId()));
        List<LookupScore> top = ranked.stream().limit(LOOKUP_MAX_MATCHES).toList();
        boolean ambiguous = top.size() > 1 && top.get(0).score() == top.get(1).score();
        List<TechVocabVOs.TermDetail> matches = top.stream()
                .map(s -> toDetail(s.term(), userId, false))
                .collect(Collectors.toList());
        return new TechVocabVOs.LookupResultVO(original.trim(), key, null, ambiguous, matches);
    }

    /** 选区清洗：NFKC → 截取英文片段 → 去首尾标点/引号 → 折叠空白 */
    private String cleanSelection(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.length() > LOOKUP_MAX_LENGTH || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return null;
        }
        if (CJK_CHAR.matcher(text).find()) {
            // 中英文混排：取最长英文片段，纯中文选区不进入词库查询
            Matcher matcher = LATIN_RUN.matcher(text);
            String best = null;
            while (matcher.find()) {
                if (best == null || matcher.group().length() > best.length()) {
                    best = matcher.group();
                }
            }
            if (best == null) {
                return "";
            }
            text = best;
        }
        text = EDGE_QUOTES.matcher(text).replaceAll("");
        text = EDGE_PUNCT.matcher(text).replaceAll("");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.length() > LOOKUP_MAX_LENGTH) {
            return null;
        }
        if (!LATIN_RUN.matcher(text).find()) {
            return "";
        }
        return text;
    }

    private String normalizeLookupKey(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** 去掉空白后比较，兼容 AbstractQueuedSynchronizer / Abstract Queued Synchronizer 两种写法 */
    private boolean compactEquals(String candidate, String keyCompact) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String compact = candidate.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return compact.equals(keyCompact);
    }

    private boolean aliasMatches(String aliases, String key) {
        if (aliases == null || aliases.isBlank()) {
            return false;
        }
        for (String alias : aliases.split(",")) {
            String normalized = normalizeLookupKey(alias);
            if (!normalized.isEmpty() && normalized.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private int matchScore(TechVocabTerm term, String key) {
        String termKey = term.getNormalizedKey() == null
                ? normalizeLookupKey(term.getHeadword()) : term.getNormalizedKey();
        String keyCompact = key.replace(" ", "");
        if (termKey.equals(key)) {
            return 100;
        }
        if (compactEquals(term.getFullForm(), keyCompact)) {
            return 92;
        }
        if (compactEquals(term.getClassName(), keyCompact)) {
            return 90;
        }
        if (aliasMatches(term.getAliases(), key)) {
            return 86;
        }
        if (key.length() >= 3 && termKey.startsWith(key + " ")) {
            return 40;
        }
        if (key.length() >= 4 && termKey.contains(key)) {
            return 20;
        }
        return 0;
    }

    private record LookupScore(TechVocabTerm term, int score) {
    }

    /* ============================ 用户状态 ============================ */

    public PageResult<TechVocabVOs.MyTermVO> myTerms(Long userId, Boolean favorite, String status,
                                                     int pageNum, int pageSize) {
        Page<UserVocabState> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<UserVocabState> wrapper = new LambdaQueryWrapper<UserVocabState>()
                .eq(UserVocabState::getUserId, userId)
                // 只展示仍然发布的词条，归档词条不在个人列表伪装为可学内容
                .apply("EXISTS (SELECT 1 FROM tech_vocab_term t WHERE t.id = user_vocab_state.term_id "
                        + "AND t.content_status = 'PUBLISHED' AND t.deleted = 0)");
        if (Boolean.TRUE.equals(favorite)) {
            wrapper.eq(UserVocabState::getFavorite, true);
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!LEARNING_STATUSES.contains(normalized)) {
                throw new BusinessException(400, "学习状态不合法");
            }
            wrapper.eq(UserVocabState::getLearningStatus, normalized);
        }
        wrapper.orderByDesc(UserVocabState::getUpdateTime).orderByDesc(UserVocabState::getId);
        Page<UserVocabState> states = stateMapper.selectPage(page, wrapper);
        List<UserVocabState> records = states.getRecords();
        if (records.isEmpty()) {
            return pageResult(List.of(), states);
        }
        List<Long> termIds = records.stream().map(UserVocabState::getTermId).collect(Collectors.toList());
        Map<Long, TechVocabTerm> termMap = termMapper.selectBatchIds(termIds).stream()
                .collect(Collectors.toMap(TechVocabTerm::getId, t -> t));
        Map<Long, List<TechVocabPronunciation>> pronunciationMap = loadPronunciations(termIds);
        List<TechVocabVOs.MyTermVO> items = records.stream()
                .map(state -> {
                    TechVocabTerm term = termMap.get(state.getTermId());
                    if (term == null) {
                        return null;
                    }
                    return new TechVocabVOs.MyTermVO(
                            toSummary(term, pronunciationMap.getOrDefault(term.getId(), List.of())),
                            state.getFavorite(), state.getLearningStatus(), state.getUpdateTime());
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return pageResult(items, states);
    }

    public TechVocabVOs.UserStateVO updateState(Long userId, Long termId, TechVocabDtos.UserStateInput input) {
        if (input == null || (input.favorite() == null && input.learningStatus() == null)) {
            throw new BusinessException(400, "至少需要提交 favorite 或 learningStatus");
        }
        if (input.learningStatus() != null) {
            String status = input.learningStatus().trim().toUpperCase(Locale.ROOT);
            if (!LEARNING_STATUSES.contains(status)) {
                throw new BusinessException(400, "学习状态不合法");
            }
        }
        requirePublished(termId);
        UserVocabState existing = stateMapper.selectOne(new LambdaQueryWrapper<UserVocabState>()
                .eq(UserVocabState::getUserId, userId)
                .eq(UserVocabState::getTermId, termId));
        if (existing == null) {
            UserVocabState state = new UserVocabState();
            state.setUserId(userId);
            state.setTermId(termId);
            state.setFavorite(Boolean.TRUE.equals(input.favorite()));
            state.setLearningStatus(input.learningStatus() == null
                    ? "NEW" : input.learningStatus().trim().toUpperCase(Locale.ROOT));
            try {
                stateMapper.insert(state);
                return toStateVO(state);
            } catch (DuplicateKeyException race) {
                // 并发首次写入：回读后走更新分支，保证幂等
                existing = stateMapper.selectOne(new LambdaQueryWrapper<UserVocabState>()
                        .eq(UserVocabState::getUserId, userId)
                        .eq(UserVocabState::getTermId, termId));
                if (existing == null) {
                    throw race;
                }
            }
        }
        if (input.favorite() != null) {
            existing.setFavorite(input.favorite());
        }
        if (input.learningStatus() != null) {
            existing.setLearningStatus(input.learningStatus().trim().toUpperCase(Locale.ROOT));
        }
        stateMapper.updateById(existing);
        return toStateVO(existing);
    }

    /* ============================ 管理端 CRUD ============================ */

    @Transactional
    public TechVocabVOs.TermDetail adminCreate(TechVocabDtos.TermInput input) {
        Normalized normalized = normalize(input, null);
        TechVocabTerm term = new TechVocabTerm();
        apply(term, normalized, true);
        try {
            termMapper.insert(term);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "同一领域下已存在相同词条义项");
        }
        replacePronunciations(term.getId(), normalized.pronunciations(), normalized.termType(), normalized.headword());
        return toDetail(termMapper.selectById(term.getId()), null, true);
    }

    @Transactional
    public TechVocabVOs.TermDetail adminUpdate(Long id, TechVocabDtos.TermInput input) {
        TechVocabTerm term = termMapper.selectById(id);
        if (term == null) {
            throw new BusinessException(404, "词条不存在");
        }
        Normalized normalized = normalize(input, id);
        apply(term, normalized, false);
        try {
            termMapper.updateById(term);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "同一领域下已存在相同词条义项");
        }
        if (normalized.pronunciations() != null) {
            replacePronunciations(term.getId(), normalized.pronunciations(), normalized.termType(), normalized.headword());
        }
        return toDetail(termMapper.selectById(term.getId()), null, true);
    }

    public TechVocabVOs.TermDetail adminDetail(Long id) {
        TechVocabTerm term = termMapper.selectById(id);
        if (term == null) {
            throw new BusinessException(404, "词条不存在");
        }
        return toDetail(term, null, true);
    }

    /* ============================ 导入预览 / 确认 ============================ */

    public TechVocabVOs.ImportPreviewVO previewImport(Long adminId, TechVocabDtos.ImportRequest request) {
        if (request == null || request.terms() == null || request.terms().isEmpty()) {
            throw new BusinessException(400, "导入内容为空");
        }
        if (request.terms().size() > 500) {
            throw new BusinessException(400, "单次最多导入 500 条词条");
        }
        cleanupExpiredPreviews();
        List<TechVocabDtos.TermInput> valid = new ArrayList<>();
        List<TechVocabVOs.ImportPreviewItem> items = new ArrayList<>();
        Set<String> keysInFile = new HashSet<>();
        int newCount = 0;
        int updateCount = 0;
        int invalidCount = 0;
        for (int i = 0; i < request.terms().size(); i++) {
            TechVocabDtos.TermInput input = request.terms().get(i);
            try {
                // 导入允许命中既有词条（UPDATE），重复义项由下方 keysInFile 单独拦截
                Normalized normalized = normalize(input, null, false);
                String key = normalized.normalizedKey() + "|" + normalized.domain() + "|" + normalized.senseKey();
                if (!keysInFile.add(key)) {
                    invalidCount++;
                    items.add(new TechVocabVOs.ImportPreviewItem(i, safe(input.headword()), safe(input.domain()),
                            "INVALID", "导入文件内存在重复义项"));
                    continue;
                }
                boolean exists = termMapper.selectCount(new LambdaQueryWrapper<TechVocabTerm>()
                        .eq(TechVocabTerm::getNormalizedKey, normalized.normalizedKey())
                        .eq(TechVocabTerm::getDomain, normalized.domain())
                        .eq(TechVocabTerm::getSenseKey, normalized.senseKey())) > 0;
                valid.add(input);
                if (exists) {
                    updateCount++;
                    items.add(new TechVocabVOs.ImportPreviewItem(i, normalized.headword(), normalized.domain(),
                            "UPDATE", "已存在，将更新内容与发音"));
                } else {
                    newCount++;
                    items.add(new TechVocabVOs.ImportPreviewItem(i, normalized.headword(), normalized.domain(),
                            "NEW", "将新增词条"));
                }
            } catch (BusinessException e) {
                invalidCount++;
                items.add(new TechVocabVOs.ImportPreviewItem(i, safe(input == null ? null : input.headword()),
                        safe(input == null ? null : input.domain()), "INVALID", e.getMessage()));
            }
        }
        String token = UUID.randomUUID().toString();
        PreviewEntry entry = new PreviewEntry();
        entry.adminId = adminId;
        entry.terms = valid;
        entry.invalidCount = invalidCount;
        entry.expiresAt = LocalDateTime.now().plusMinutes(PREVIEW_TTL_MINUTES);
        previews.put(token, entry);
        LocalDateTime expiresAt = entry.expiresAt;
        int previewLimit = Math.min(items.size(), 50);
        return new TechVocabVOs.ImportPreviewVO(token, newCount, updateCount, invalidCount,
                new ArrayList<>(items.subList(0, previewLimit)), expiresAt);
    }

    @Transactional
    public TechVocabVOs.ImportResultVO confirmImport(Long adminId, String token) {
        PreviewEntry entry = token == null ? null : previews.get(token);
        if (entry == null || entry.expiresAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "导入预览不存在或已过期，请重新预览");
        }
        if (!entry.adminId.equals(adminId)) {
            throw new BusinessException(403, "导入预览不属于当前用户");
        }
        synchronized (entry) {
            if (entry.result != null) {
                // 同一 token 重复确认：返回首次回执，不重复写库
                TechVocabVOs.ImportResultVO r = entry.result;
                return new TechVocabVOs.ImportResultVO(r.created(), r.updated(), r.skipped(), r.invalid(), true);
            }
            int created = 0;
            int updated = 0;
            int skipped = 0;
            for (TechVocabDtos.TermInput input : entry.terms) {
                Normalized normalized = normalize(input, null, false);
                TechVocabTerm existing = termMapper.selectOne(new LambdaQueryWrapper<TechVocabTerm>()
                        .eq(TechVocabTerm::getNormalizedKey, normalized.normalizedKey())
                        .eq(TechVocabTerm::getDomain, normalized.domain())
                        .eq(TechVocabTerm::getSenseKey, normalized.senseKey()));
                if (existing == null) {
                    TechVocabTerm term = new TechVocabTerm();
                    apply(term, normalized, true);
                    try {
                        termMapper.insert(term);
                        replacePronunciations(term.getId(), normalized.pronunciations(),
                                normalized.termType(), normalized.headword());
                        created++;
                    } catch (DuplicateKeyException race) {
                        skipped++;
                        log.warn("导入并发写入词条被跳过: {}", normalized.headword());
                    }
                } else {
                    apply(existing, normalized, false);
                    termMapper.updateById(existing);
                    replacePronunciations(existing.getId(), normalized.pronunciations(),
                            normalized.termType(), normalized.headword());
                    updated++;
                }
            }
            TechVocabVOs.ImportResultVO result = new TechVocabVOs.ImportResultVO(
                    created, updated, skipped, entry.invalidCount, false);
            // 保留 token 供同一预览重复确认读取回执（幂等），过期后由 cleanupExpiredPreviews 清理
            entry.result = result;
            return result;
        }
    }

    /* ============================ 内部实现 ============================ */

    private LambdaQueryWrapper<TechVocabTerm> publishedWrapper() {
        return new LambdaQueryWrapper<TechVocabTerm>().eq(TechVocabTerm::getContentStatus, "PUBLISHED");
    }

    private void applyType(LambdaQueryWrapper<TechVocabTerm> wrapper, String type) {
        if (type == null || type.isBlank()) {
            return;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!TERM_TYPES.contains(normalized)) {
            throw new BusinessException(400, "词条类型不合法");
        }
        wrapper.eq(TechVocabTerm::getTermType, normalized);
    }

    /** 关键词模糊搜索：主词 / 归一化键 / 全称 / 中文释义 / 类名 / 别名 */
    private void applyKeyword(LambdaQueryWrapper<TechVocabTerm> wrapper, String q) {
        if (q == null || q.isBlank()) {
            return;
        }
        // 去掉 LIKE 通配符，避免用户输入 % 或 _ 扩大扫描范围
        String keyword = q.trim().replace("%", "").replace("_", "");
        if (keyword.isEmpty()) {
            return;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        wrapper.and(w -> w.like(TechVocabTerm::getHeadword, keyword)
                .or().like(TechVocabTerm::getNormalizedKey, lower)
                .or().like(TechVocabTerm::getFullForm, keyword)
                .or().like(TechVocabTerm::getMeaningCn, keyword)
                .or().like(TechVocabTerm::getClassName, keyword)
                .or().like(TechVocabTerm::getAliases, keyword));
    }

    private TechVocabTerm requirePublished(Long id) {
        TechVocabTerm term = id == null ? null : termMapper.selectById(id);
        if (term == null || !"PUBLISHED".equals(term.getContentStatus())) {
            throw new BusinessException(404, "词条不存在或未发布");
        }
        return term;
    }

    private PageResult<TechVocabVOs.TermSummary> summaryPage(Page<TechVocabTerm> page) {
        List<TechVocabTerm> terms = page.getRecords();
        Map<Long, List<TechVocabPronunciation>> pronunciationMap = loadPronunciations(
                terms.stream().map(TechVocabTerm::getId).collect(Collectors.toList()));
        List<TechVocabVOs.TermSummary> summaries = terms.stream()
                .map(t -> toSummary(t, pronunciationMap.getOrDefault(t.getId(), List.of())))
                .collect(Collectors.toList());
        PageResult<TechVocabVOs.TermSummary> result = new PageResult<>();
        result.setRecords(summaries);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    private <T> PageResult<T> pageResult(List<T> records, Page<?> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    private TechVocabVOs.TermSummary toSummary(TechVocabTerm term, List<TechVocabPronunciation> pronunciations) {
        List<String> variants = playable(pronunciations).stream()
                .map(TechVocabPronunciation::getVariant)
                .distinct()
                .collect(Collectors.toList());
        return new TechVocabVOs.TermSummary(term.getId(), term.getHeadword(), term.getTermType(),
                term.getDomain(), term.getMeaningCn(), term.getFullForm(), term.getClassName(),
                term.getIpaUs(), term.getIpaUk(), !variants.isEmpty(), variants);
    }

    private TechVocabVOs.TermDetail toDetail(TechVocabTerm term, Long userId, boolean adminView) {
        List<TechVocabPronunciation> pronunciations = loadPronunciations(List.of(term.getId()))
                .getOrDefault(term.getId(), List.of());
        List<TechVocabConceptLink> links = linkMapper.selectList(new LambdaQueryWrapper<TechVocabConceptLink>()
                .eq(TechVocabConceptLink::getTermId, term.getId())
                .orderByAsc(TechVocabConceptLink::getId));
        List<TechVocabVOs.RelatedTermVO> related = new ArrayList<>();
        List<TechVocabVOs.ConceptLinkVO> concepts = new ArrayList<>();
        for (TechVocabConceptLink link : links) {
            if ("TERM".equals(link.getTargetType())) {
                TechVocabTerm target = termMapper.selectById(link.getTargetId());
                if (target != null && (adminView || "PUBLISHED".equals(target.getContentStatus()))) {
                    related.add(new TechVocabVOs.RelatedTermVO(target.getId(), target.getHeadword(),
                            target.getMeaningCn(), target.getDomain(), link.getRelationType()));
                }
            } else {
                TechVocabVOs.ConceptLinkVO vo = resolveTarget(link, userId);
                if (vo != null) {
                    concepts.add(vo);
                }
            }
        }
        UserVocabState state = userId == null ? null : stateMapper.selectOne(new LambdaQueryWrapper<UserVocabState>()
                .eq(UserVocabState::getUserId, userId)
                .eq(UserVocabState::getTermId, term.getId()));
        return new TechVocabVOs.TermDetail(term.getId(), term.getHeadword(), term.getTermType(), term.getDomain(),
                term.getSenseKey(), term.getFullForm(), term.getClassName(), term.getPartOfSpeech(),
                term.getIpaUs(), term.getIpaUk(), term.getMeaningCn(), term.getDefinitionCn(),
                term.getDefinitionEn(), parseBreakdown(term.getWordBreakdown()), term.getBreakdownNote(),
                term.getInterviewEn(), term.getExampleEn(),
                term.getExampleCn(), splitAliases(term.getAliases()), term.getContentStatus(),
                term.getReferenceSource(), term.getLicenseNote(), term.getReviewedAt(),
                pronunciations.stream().map(this::toPronunciationVO).collect(Collectors.toList()),
                concepts, related,
                state != null && Boolean.TRUE.equals(state.getFavorite()),
                state == null ? null : state.getLearningStatus());
    }

    /** 关联目标二次鉴权：无权限/已删除时返回 null，不泄漏标题 */
    private TechVocabVOs.ConceptLinkVO resolveTarget(TechVocabConceptLink link, Long userId) {
        // 笔记是用户私有数据；章节阅读入口位于 /api/learning/** 之下要求登录，
        // 匿名用户既无权访问也不应看到章节标题，因此这两类关联都只对登录用户展示
        if (userId == null && ("NOTE".equals(link.getTargetType()) || "CHAPTER".equals(link.getTargetType()))) {
            return null;
        }
        if (!LINK_TYPES.contains(link.getTargetType())) {
            return null;
        }
        switch (link.getTargetType()) {
            case "DOCUMENT" -> {
                DocDocument doc = docMapper.selectById(link.getTargetId());
                // 与文档详情同一可见性规则：发布状态 + 知识库 Owner/Editor/Reader 归属都通过才可读，
                // 未授权私有文档不能从术语关联里泄漏标题、片段或摘要
                if (docAccess.canRead(doc, userId)) {
                    return new TechVocabVOs.ConceptLinkVO("DOCUMENT", doc.getId(), link.getRelationType(),
                            doc.getTitle(), "/doc/" + doc.getId());
                }
            }
            case "CHAPTER" -> {
                LearningChapter chapter = chapterMapper.selectById(link.getTargetId());
                if (chapter != null) {
                    return new TechVocabVOs.ConceptLinkVO("CHAPTER", chapter.getId(), link.getRelationType(),
                            chapter.getTitle(), "/learning/chapter/" + chapter.getId());
                }
            }
            case "KNOWLEDGE" -> {
                KgEntity entity = kgEntityMapper.selectById(link.getTargetId());
                // 知识单元继承所属知识库分类权限；跨库/无权限实体不展示
                if (docAccess.canReadKnowledge(entity, userId)) {
                    return new TechVocabVOs.ConceptLinkVO("KNOWLEDGE", entity.getId(), link.getRelationType(),
                            entity.getName(), null);
                }
            }
            case "NOTE" -> {
                LearningNote note = noteMapper.selectById(link.getTargetId());
                if (note != null && userId != null && userId.equals(note.getUserId())) {
                    return new TechVocabVOs.ConceptLinkVO("NOTE", note.getId(), link.getRelationType(),
                            note.getTitle(), null);
                }
            }
            default -> {
                return null;
            }
        }
        return null;
    }

    private Map<Long, List<TechVocabPronunciation>> loadPronunciations(List<Long> termIds) {
        if (termIds == null || termIds.isEmpty()) {
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
                .sorted(Comparator.comparing(TechVocabPronunciation::getId))
                .collect(Collectors.toList());
    }

    private TechVocabVOs.PronunciationVO toPronunciationVO(TechVocabPronunciation p) {
        return new TechVocabVOs.PronunciationVO(p.getId(), p.getVariant(), p.getReadingText(), p.getLocale(),
                p.getReadingMode(), p.getAudioUrl(), p.getAudioProvider(), p.getLicenseNote(),
                p.getVerificationStatus());
    }

    private TechVocabVOs.UserStateVO toStateVO(UserVocabState state) {
        return new TechVocabVOs.UserStateVO(state.getTermId(), state.getFavorite(),
                state.getLearningStatus(), state.getUpdateTime());
    }

    private List<TechVocabDtos.WordPart> parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TechVocabDtos.WordPart>>() {
            });
        } catch (Exception e) {
            log.warn("词条逐词拆解 JSON 解析失败，按空处理");
            return List.of();
        }
    }

    private List<String> splitAliases(String aliases) {
        if (aliases == null || aliases.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(aliases.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /* ============================ 校验与写入 ============================ */

    private Normalized normalize(TechVocabDtos.TermInput input, Long excludeId) {
        return normalize(input, excludeId, true);
    }

    /**
     * @param checkDuplicate 创建/编辑需拦截同领域重复义项；导入路径由调用方自行判定
     *                       NEW / UPDATE，重复义项在预览阶段单独拒绝，不能再抛 409
     */
    private Normalized normalize(TechVocabDtos.TermInput input, Long excludeId, boolean checkDuplicate) {
        if (input == null) {
            throw new BusinessException(400, "词条内容不能为空");
        }
        String headword = trim(input.headword());
        if (headword.isEmpty()) {
            throw new BusinessException(400, "headword 不能为空");
        }
        if (headword.length() > 200) {
            throw new BusinessException(400, "headword 过长（最多 200 字符）");
        }
        String termType = trim(input.termType()).toUpperCase(Locale.ROOT);
        if (!TERM_TYPES.contains(termType)) {
            throw new BusinessException(400, "termType 必须是 WORD / ACRONYM / PHRASE");
        }
        String domain = trim(input.domain()).toUpperCase(Locale.ROOT);
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new BusinessException(400, "domain 必须是大写字母/数字/下划线，长度 2-40");
        }
        String senseKey = trim(input.senseKey()).toLowerCase(Locale.ROOT);
        if (senseKey.isEmpty()) {
            senseKey = "default";
        }
        if (!SENSE_PATTERN.matcher(senseKey).matches()) {
            throw new BusinessException(400, "senseKey 只能包含小写字母、数字和连字符");
        }
        String fullForm = trim(input.fullForm());
        if ("ACRONYM".equals(termType) && fullForm.isEmpty()) {
            throw new BusinessException(400, "缩写词条必须填写英文全称 fullForm");
        }
        String meaningCn = trim(input.meaningCn());
        if (meaningCn.isEmpty()) {
            throw new BusinessException(400, "中文释义 meaningCn 不能为空");
        }
        String status = trim(input.contentStatus()).toUpperCase(Locale.ROOT);
        if (status.isEmpty()) {
            status = "DRAFT";
        }
        if (!CONTENT_STATUSES.contains(status)) {
            throw new BusinessException(400, "contentStatus 不合法");
        }
        String reference = trim(input.referenceSource());
        if ("PUBLISHED".equals(status) && reference.isEmpty()) {
            throw new BusinessException(400, "发布词条必须填写来源 referenceSource");
        }
        List<String> aliases = input.aliases() == null ? List.of() : input.aliases().stream()
                .map(this::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (aliases.size() > 20) {
            throw new BusinessException(400, "别名最多 20 个");
        }
        String aliasText = aliases.isEmpty() ? null : String.join(",", aliases);
        if (aliasText != null && aliasText.length() > 500) {
            throw new BusinessException(400, "别名总长度不能超过 500 字符");
        }
        String breakdownJson = null;
        if (input.wordBreakdown() != null && !input.wordBreakdown().isEmpty()) {
            try {
                breakdownJson = objectMapper.writeValueAsString(input.wordBreakdown());
            } catch (Exception e) {
                throw new BusinessException(400, "逐词拆解格式不正确");
            }
        }
        String normalizedKey = headword.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        // 编辑时排除自身，避免唯一键误判
        if (checkDuplicate && excludeId == null) {
            long duplicate = termMapper.selectCount(new LambdaQueryWrapper<TechVocabTerm>()
                    .eq(TechVocabTerm::getNormalizedKey, normalizedKey)
                    .eq(TechVocabTerm::getDomain, domain)
                    .eq(TechVocabTerm::getSenseKey, senseKey));
            if (duplicate > 0) {
                throw new BusinessException(409, "同一领域下已存在相同词条义项");
            }
        }
        List<TechVocabDtos.PronunciationInput> pronunciations = normalizePronunciations(input.pronunciations());
        return new Normalized(headword, normalizedKey, termType, senseKey, domain, fullForm,
                trim(input.className()), trim(input.partOfSpeech()), trim(input.ipaUs()), trim(input.ipaUk()),
                meaningCn, trim(input.definitionCn()), trim(input.definitionEn()), breakdownJson,
                trim(input.breakdownNote()), trim(input.interviewEn()),
                trim(input.exampleEn()), trim(input.exampleCn()), aliasText, status, reference,
                trim(input.licenseNote()), pronunciations);
    }

    private List<TechVocabDtos.PronunciationInput> normalizePronunciations(
            List<TechVocabDtos.PronunciationInput> inputs) {
        if (inputs == null) {
            return null;
        }
        List<TechVocabDtos.PronunciationInput> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TechVocabDtos.PronunciationInput p : inputs) {
            if (p == null) {
                throw new BusinessException(400, "发音变体不能为空");
            }
            String variant = trim(p.variant()).toUpperCase(Locale.ROOT);
            if (!VARIANTS.contains(variant)) {
                throw new BusinessException(400, "发音变体必须是 WORD_US / WORD_UK / ABBR / FULL_FORM");
            }
            if (!seen.add(variant)) {
                throw new BusinessException(400, "同一发音变体不能重复");
            }
            String status = trim(p.verificationStatus()).toUpperCase(Locale.ROOT);
            if (status.isEmpty()) {
                status = "PENDING";
            }
            if (!VERIFY_STATUSES.contains(status)) {
                throw new BusinessException(400, "发音校验状态不合法");
            }
            String readingText = trim(p.readingText());
            if ("VERIFIED".equals(status) && readingText.isEmpty()) {
                throw new BusinessException(400, "已核验发音必须提供 readingText");
            }
            String mode = trim(p.readingMode()).toUpperCase(Locale.ROOT);
            if (mode.isEmpty()) {
                mode = "ABBR".equals(variant) ? "LETTER_BY_LETTER" : "SPOKEN_WORD";
            }
            if (!READING_MODES.contains(mode)) {
                throw new BusinessException(400, "readingMode 不合法");
            }
            String audioUrl = trim(p.audioUrl());
            if (!audioUrl.isEmpty() && !(audioUrl.startsWith("https://") || audioUrl.startsWith("http://"))) {
                throw new BusinessException(400, "音频地址只允许 http/https");
            }
            String locale = trim(p.locale());
            if (locale.isEmpty()) {
                locale = "WORD_UK".equals(variant) ? "en-GB" : "en-US";
            }
            result.add(new TechVocabDtos.PronunciationInput(variant, readingText, locale, mode,
                    audioUrl.isEmpty() ? null : audioUrl, trim(p.audioProvider()),
                    trim(p.licenseNote()), status));
        }
        return result;
    }

    private void apply(TechVocabTerm term, Normalized n, boolean create) {
        term.setHeadword(n.headword());
        term.setNormalizedKey(n.normalizedKey());
        term.setTermType(n.termType());
        term.setSenseKey(n.senseKey());
        term.setDomain(n.domain());
        term.setFullForm(n.fullForm());
        term.setClassName(n.className());
        term.setPartOfSpeech(n.partOfSpeech());
        term.setIpaUs(n.ipaUs());
        term.setIpaUk(n.ipaUk());
        term.setMeaningCn(n.meaningCn());
        term.setDefinitionCn(n.definitionCn());
        term.setDefinitionEn(n.definitionEn());
        term.setWordBreakdown(n.wordBreakdownJson());
        term.setBreakdownNote(n.breakdownNote());
        term.setInterviewEn(n.interviewEn());
        term.setExampleEn(n.exampleEn());
        term.setExampleCn(n.exampleCn());
        term.setAliases(n.aliases());
        term.setContentStatus(n.contentStatus());
        term.setReferenceSource(n.referenceSource());
        term.setLicenseNote(n.licenseNote());
        boolean reviewed = "PUBLISHED".equals(n.contentStatus()) || "REVIEWED".equals(n.contentStatus());
        if (reviewed) {
            term.setReviewedAt(LocalDateTime.now());
        } else {
            term.setReviewedAt(null);
        }
    }

    /** 替换发音子行：物理删除后重建，避免逻辑删除值累积触发 (term_id, variant, deleted) 唯一键冲突 */
    private void replacePronunciations(Long termId, List<TechVocabDtos.PronunciationInput> inputs,
                                       String termType, String headword) {
        jdbc.update("DELETE FROM tech_vocab_pronunciation WHERE term_id = ?", termId);
        List<TechVocabDtos.PronunciationInput> rows = inputs;
        if (rows == null) {
            rows = defaultPronunciations(termType, headword);
        }
        for (TechVocabDtos.PronunciationInput p : rows) {
            TechVocabPronunciation row = new TechVocabPronunciation();
            row.setTermId(termId);
            row.setVariant(p.variant());
            row.setReadingText(p.readingText());
            row.setLocale(p.locale());
            row.setReadingMode(p.readingMode());
            row.setAudioUrl(p.audioUrl());
            row.setAudioProvider(p.audioProvider());
            row.setLicenseNote(p.licenseNote());
            row.setVerificationStatus(p.verificationStatus());
            pronunciationMapper.insert(row);
        }
    }

    /**
     * 未显式提供发音时只生成“待审核”占位：
     * 单词录入 headword 作为朗读文本；缩写绝不自动推断逐字母读法。
     */
    private List<TechVocabDtos.PronunciationInput> defaultPronunciations(String termType, String headword) {
        if ("ACRONYM".equals(termType)) {
            return List.of(
                    new TechVocabDtos.PronunciationInput("ABBR", null, "en-US", "LETTER_BY_LETTER",
                            null, null, null, "PENDING"),
                    new TechVocabDtos.PronunciationInput("FULL_FORM", null, "en-US", "SPOKEN_WORD",
                            null, null, null, "PENDING"));
        }
        return List.of(
                new TechVocabDtos.PronunciationInput("WORD_US", headword, "en-US", "SPOKEN_WORD",
                        null, null, null, "PENDING"),
                new TechVocabDtos.PronunciationInput("WORD_UK", headword, "en-GB", "SPOKEN_WORD",
                        null, null, null, "PENDING"));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int normalizePageNum(int pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private void cleanupExpiredPreviews() {
        LocalDateTime now = LocalDateTime.now();
        previews.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    /** 校验后的规范化词条数据 */
    private record Normalized(String headword, String normalizedKey, String termType, String senseKey,
                              String domain, String fullForm, String className, String partOfSpeech,
                              String ipaUs, String ipaUk, String meaningCn, String definitionCn,
                              String definitionEn, String wordBreakdownJson, String breakdownNote,
                              String interviewEn, String exampleEn,
                              String exampleCn, String aliases, String contentStatus,
                              String referenceSource, String licenseNote,
                              List<TechVocabDtos.PronunciationInput> pronunciations) {
    }

    /** 导入预览短期状态；result 非空表示已执行，用于幂等回执 */
    private static final class PreviewEntry {
        private Long adminId;
        private List<TechVocabDtos.TermInput> terms;
        private int invalidCount;
        private LocalDateTime expiresAt;
        private volatile TechVocabVOs.ImportResultVO result;
    }
}
