package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.TechVocabDtos;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningNote;
import com.knowflow.entity.TechVocabCandidate;
import com.knowflow.entity.TechVocabTerm;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningNoteMapper;
import com.knowflow.mapper.TechVocabCandidateMapper;
import com.knowflow.mapper.TechVocabTermMapper;
import com.knowflow.vo.TechVocabVOs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 技术英语 P2 提词服务：从技术文章 / 个人笔记中提取候选英文术语。
 *
 * 诚实降级原则：
 * - 模型未配置或调用失败时，只做基于规则的候选检测，并明确告知前端“本次为规则提取”；
 * - AI 生成的释义与英文全称一律作为待核验内容，不写入 VERIFIED、不直接发布到公共词库；
 * - 任何候选都必须经用户确认后才保存到个人候选表，管理员审核通过后才建立正式词条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechVocabExtractionService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 60;
    private static final int MAX_SOURCE_TEXT = 200_000;
    private static final Set<String> TYPES = Set.of("WORD", "ACRONYM", "PHRASE");
    private static final Set<String> SOURCE_TYPES = Set.of("DOCUMENT", "NOTE", "CHAPTER", "TEXT");
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
    /** 与正式词条一致的领域枚举；AI 返回其他领域一律回落到来源推断值 */
    private static final Set<String> DOMAINS = Set.of(
            "JAVA", "JVM", "CONCURRENCY", "SPRING", "DB", "CACHE",
            "MQ", "DISTRIBUTED", "LLM", "RAG", "AGENT");
    private static final Pattern HEADWORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9 +#./_-]{0,59}");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");
    private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z][A-Z0-9]{1,6}\\b");
    private static final Pattern PHRASE = Pattern.compile("\\b[A-Z][a-z]+(?:[ -][A-Z][a-z]+){1,3}\\b");
    private static final Pattern WORD = Pattern.compile("\\b[a-z][a-z-]{3,24}\\b");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。！？\\n]");

    /** 常见英文虚词 / 通用词，避免把普通英语当技术词汇 */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "have", "has", "had", "are", "was", "were",
            "will", "would", "could", "should", "can", "may", "might", "must", "not", "but", "you", "your",
            "they", "them", "their", "there", "then", "than", "when", "where", "which", "while", "what",
            "who", "how", "why", "all", "any", "some", "more", "most", "much", "many", "also", "into",
            "over", "under", "about", "after", "before", "between", "through", "because", "been", "being",
            "does", "did", "doing", "each", "other", "such", "only", "same", "very", "just", "like",
            "make", "makes", "made", "use", "used", "uses", "using", "one", "two", "three", "here",
            "these", "those", "its", "our", "out", "off", "per", "via", "example", "method", "class",
            "code", "line", "page", "note", "notes", "text", "case", "cases", "part", "parts", "item",
            "true", "false", "null", "string", "number", "value", "values", "type", "types");

    private final TechVocabTermMapper termMapper;
    private final TechVocabCandidateMapper candidateMapper;
    private final DocDocumentMapper docMapper;
    private final LearningNoteMapper noteMapper;
    private final com.knowflow.service.AiService aiService;
    private final TechVocabService techVocabService;
    private final com.knowflow.security.DocumentAccessPolicy docAccess;
    private final ObjectMapper objectMapper;

    /* ============================ 候选提取 ============================ */

    /**
     * 提取候选术语。不写库，返回预览供用户勾选确认。
     *
     * @param userId 当前登录用户；null 表示匿名（只允许 TEXT 来源）
     */
    public TechVocabVOs.ExtractionResultVO extract(Long userId, TechVocabDtos.ExtractInput input) {
        SourceText source = resolveSource(userId, input);
        int limit = input == null || input.limit() == null ? DEFAULT_LIMIT : input.limit();
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));
        List<String> types = normalizeTypes(input == null ? null : input.types());

        Map<String, TechVocabTerm> library = loadLibrary();
        List<TechVocabVOs.ExtractedCandidateVO> ruleItems = ruleExtract(source.text(), types, limit, library);
        boolean useAi = input != null && Boolean.TRUE.equals(input.useAi());
        boolean aiAvailable = aiService.isConfigured(userId);

        if (!useAi) {
            String note = aiAvailable
                    ? "本次按规则提取（未调用模型）；如需 AI 建议释义，请在弹窗中勾选使用 AI"
                    : "未配置可用 AI 模型，本次为规则提取，不代表 AI 结果";
            return new TechVocabVOs.ExtractionResultVO("RULE", aiAvailable, note,
                    source.text().length(), ruleItems.size() >= limit, ruleItems);
        }
        if (!aiAvailable) {
            return new TechVocabVOs.ExtractionResultVO("RULE", false,
                    "未配置可用 AI 模型，已回退为规则提取；候选释义需人工补齐",
                    source.text().length(), ruleItems.size() >= limit, ruleItems);
        }
        try {
            List<TechVocabVOs.ExtractedCandidateVO> aiItems = aiExtract(userId, source, types, limit, library);
            if (aiItems.isEmpty()) {
                return new TechVocabVOs.ExtractionResultVO("RULE", true,
                        "AI 未返回可用候选，已回退为规则提取",
                        source.text().length(), ruleItems.size() >= limit, ruleItems);
            }
            return new TechVocabVOs.ExtractionResultVO("AI", true,
                    "AI 建议仅供预览：释义与英文全称需人工核验后才能进入公共词库",
                    source.text().length(), aiItems.size() >= limit, aiItems);
        } catch (Exception e) {
            log.warn("技术英语 AI 提词失败，回退规则提取：{}", e.getMessage());
            return new TechVocabVOs.ExtractionResultVO("RULE", true,
                    "AI 提取失败，已回退为规则提取（未使用模型结果）",
                    source.text().length(), ruleItems.size() >= limit, ruleItems);
        }
    }

    private record SourceText(String text, String sourceType, Long sourceId, String sourceTitle, String domainHint) {
    }

    private SourceText resolveSource(Long userId, TechVocabDtos.ExtractInput input) {
        if (input == null) {
            throw new BusinessException(400, "缺少提取参数");
        }
        // 选区提取：只处理选区文本，但来源仍按文档 / 笔记鉴权，私人文档标题不会因选区提取而泄漏
        if ("SELECTION".equalsIgnoreCase(input.scope()) && input.text() != null && !input.text().isBlank()) {
            String selected = limitText(input.text());
            if (input.documentId() != null) {
                DocDocument doc = docMapper.selectById(input.documentId());
                // 与文档详情同一可见性规则（发布状态 + 知识库归属/租户成员）；无权限统一 404 避免探测
                if (!docAccess.canRead(doc, userId)) {
                    throw new BusinessException(404, "文档不存在或不可访问");
                }
                return new SourceText(selected, "DOCUMENT", doc.getId(), doc.getTitle(), domainHint(doc.getTags(), selected));
            }
            if (input.noteId() != null) {
                if (userId == null) {
                    throw new BusinessException(401, "未登录或登录已失效");
                }
                LearningNote note = noteMapper.selectById(input.noteId());
                if (note == null || !userId.equals(note.getUserId())) {
                    throw new BusinessException(404, "笔记不存在或不可访问");
                }
                return new SourceText(selected, "NOTE", note.getId(), note.getTitle(), domainHint(note.getTags(), selected));
            }
            return new SourceText(selected, "TEXT", null, null, domainHint(null, selected));
        }
        if (input.documentId() != null) {
            DocDocument doc = docMapper.selectById(input.documentId());
            // 与文档详情同一可见性规则：未发布或无知识库权限不参与提取，也不泄漏标题
            if (!docAccess.canRead(doc, userId)) {
                throw new BusinessException(404, "文档不存在或不可访问");
            }
            String text = limitText(doc.getContent());
            return new SourceText(text, "DOCUMENT", doc.getId(), doc.getTitle(), domainHint(doc.getTags(), text));
        }
        if (input.noteId() != null) {
            if (userId == null) {
                throw new BusinessException(401, "未登录或登录已失效");
            }
            LearningNote note = noteMapper.selectById(input.noteId());
            // 他人笔记一律 404，不区分“不存在”和“无权限”，避免探测
            if (note == null || !userId.equals(note.getUserId())) {
                throw new BusinessException(404, "笔记不存在或不可访问");
            }
            String text = limitText(note.getContent());
            return new SourceText(text, "NOTE", note.getId(), note.getTitle(), domainHint(note.getTags(), text));
        }
        String text = limitText(input.text());
        if (text.isBlank()) {
            throw new BusinessException(400, "请先选择要提取的正文或选区");
        }
        return new SourceText(text, "TEXT", null, null, domainHint(null, text));
    }

    private String limitText(String text) {
        String value = text == null ? "" : text;
        if (value.length() > MAX_SOURCE_TEXT) {
            throw new BusinessException(400, "待提取文本过长，请缩小范围后重试");
        }
        return value;
    }

    private List<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of("WORD", "ACRONYM", "PHRASE");
        }
        List<String> result = types.stream()
                .filter(t -> t != null && TYPES.contains(t.trim().toUpperCase(Locale.ROOT)))
                .map(t -> t.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
        return result.isEmpty() ? List.of("WORD", "ACRONYM", "PHRASE") : result;
    }

    /**
     * 依据标签与正文关键词推断领域；只用于排序与默认值，不代表已核验分类。
     * 取值必须与正式词条 domain 枚举一致（JAVA/JVM/CONCURRENCY/SPRING/DB/CACHE/MQ/LLM/RAG/AGENT），
     * 否则候选词条审核后无法与词库领域对齐。
     */
    private String domainHint(String tags, String text) {
        String meta = ((tags == null ? "" : tags) + " " + text).toLowerCase(Locale.ROOT);
        LinkedHashMap<String, String> rules = new LinkedHashMap<>();
        rules.put("mysql", "DB");
        rules.put("数据库", "DB");
        rules.put("redis", "CACHE");
        rules.put("缓存", "CACHE");
        rules.put("kafka", "MQ");
        rules.put("rabbitmq", "MQ");
        rules.put("消息队列", "MQ");
        rules.put("rag", "RAG");
        rules.put("embedding", "RAG");
        rules.put("检索增强", "RAG");
        rules.put("llm", "LLM");
        rules.put("大模型", "LLM");
        rules.put("agent", "AGENT");
        rules.put("智能体", "AGENT");
        rules.put("spring", "SPRING");
        rules.put("jvm", "JVM");
        rules.put("垃圾回收", "JVM");
        rules.put("gc ", "JVM");
        rules.put("并发", "CONCURRENCY");
        rules.put("concurren", "CONCURRENCY");
        rules.put("distributed", "DISTRIBUTED");
        rules.put("分布式", "DISTRIBUTED");
        rules.put("java", "JAVA");
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if (meta.contains(rule.getKey())) {
                return rule.getValue();
            }
        }
        return "JAVA";
    }

    private Map<String, TechVocabTerm> loadLibrary() {
        List<TechVocabTerm> terms = termMapper.selectList(new LambdaQueryWrapper<TechVocabTerm>()
                .eq(TechVocabTerm::getContentStatus, "PUBLISHED"));
        Map<String, TechVocabTerm> map = new HashMap<>();
        for (TechVocabTerm term : terms) {
            map.putIfAbsent(normalizeKey(term.getHeadword()), term);
            if (term.getFullForm() != null && !term.getFullForm().isBlank()) {
                map.putIfAbsent(normalizeKey(term.getFullForm()), term);
            }
            if (term.getAliases() != null && !term.getAliases().isBlank()) {
                for (String alias : term.getAliases().split(",")) {
                    if (!alias.isBlank()) {
                        map.putIfAbsent(normalizeKey(alias), term);
                    }
                }
            }
        }
        return map;
    }

    private List<TechVocabVOs.ExtractedCandidateVO> ruleExtract(String text, List<String> types, int limit,
                                                                Map<String, TechVocabTerm> library) {
        String domain = domainHint(null, text);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> typeOf = new HashMap<>();
        if (types.contains("ACRONYM")) {
            collect(ACRONYM.matcher(text), counts, typeOf, "ACRONYM", text);
        }
        if (types.contains("PHRASE")) {
            collect(PHRASE.matcher(text), counts, typeOf, "PHRASE", text);
        }
        if (types.contains("WORD")) {
            Matcher word = WORD.matcher(text);
            while (word.find()) {
                String value = word.group();
                if (STOPWORDS.contains(value)) {
                    continue;
                }
                String key = normalizeKey(value);
                counts.merge(key, 1, Integer::sum);
                typeOf.putIfAbsent(key, "WORD");
            }
        }
        List<Map.Entry<String, Integer>> ranked = counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> weight(typeOf.get(e.getKey())))
                        .reversed()
                        .thenComparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .collect(Collectors.toList());
        List<TechVocabVOs.ExtractedCandidateVO> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            String key = entry.getKey();
            String headword = displayHeadword(text, key, typeOf.get(key));
            if (headword == null) {
                continue;
            }
            TechVocabTerm existing = library.get(key);
            int confidence = existing != null ? 95 : baseConfidence(typeOf.get(key));
            items.add(new TechVocabVOs.ExtractedCandidateVO(
                    headword, typeOf.get(key), domain, null, null, null,
                    contextOf(text, headword), confidence, "RULE",
                    existing != null, existing == null ? null : existing.getId(),
                    existing == null ? null : existing.getHeadword()));
        }
        return items;
    }

    private void collect(Matcher matcher, Map<String, Integer> counts, Map<String, String> typeOf,
                         String type, String text) {
        while (matcher.find()) {
            String value = matcher.group();
            String key = normalizeKey(value);
            counts.merge(key, 1, Integer::sum);
            typeOf.putIfAbsent(key, type);
        }
    }

    /** 依据原文还原原始大小写写法，避免把 AQS 输出成 aqs */
    private String displayHeadword(String text, String key, String type) {
        Pattern pattern = "ACRONYM".equals(type) ? ACRONYM : ("PHRASE".equals(type) ? PHRASE : WORD);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (normalizeKey(matcher.group()).equals(key)) {
                return matcher.group();
            }
        }
        return key;
    }

    private int weight(String type) {
        if ("ACRONYM".equals(type)) {
            return 3;
        }
        if ("PHRASE".equals(type)) {
            return 2;
        }
        return 1;
    }

    private int baseConfidence(String type) {
        if ("ACRONYM".equals(type)) {
            return 78;
        }
        if ("PHRASE".equals(type)) {
            return 60;
        }
        return 40;
    }

    /** 取首次出现所在句子作为上下文，最长 200 字 */
    private String contextOf(String text, String headword) {
        int index = text.toLowerCase(Locale.ROOT).indexOf(headword.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return null;
        }
        int start = index;
        while (start > 0 && index - start < 120) {
            char c = text.charAt(start - 1);
            if (SENTENCE_END.matcher(String.valueOf(c)).find()) {
                break;
            }
            start--;
        }
        int end = index;
        while (end < text.length() && end - index < 200) {
            char c = text.charAt(end);
            end++;
            if (SENTENCE_END.matcher(String.valueOf(c)).find()) {
                break;
            }
        }
        String snippet = text.substring(start, end).trim().replaceAll("\\s+", " ");
        return snippet.length() > 200 ? snippet.substring(0, 200) : snippet;
    }

    /* ============================ AI 提取 ============================ */

    private List<TechVocabVOs.ExtractedCandidateVO> aiExtract(Long userId, SourceText source,
                                                              List<String> types, int limit,
                                                              Map<String, TechVocabTerm> library) {
        String systemPrompt = """
                你是技术英语术语抽取助手。只输出 JSON，不要输出任何解释文字。
                输出格式：{"terms":[{"headword":"英文术语","termType":"WORD|ACRONYM|PHRASE","domain":"JAVA|JVM|CONCURRENCY|SPRING|DB|CACHE|MQ|DISTRIBUTED|LLM|RAG|AGENT","fullForm":"英文全称或null","meaningCn":"中文释义","definitionCn":"一句话中文技术解释"}]}
                规则：
                1. 只抽取 Java、Spring、MySQL、Redis、Kafka、分布式、LLM、RAG、Agent 等计算机技术领域的高频英文术语；
                2. 不要抽取普通英语单词、人名、公司名、代码变量名；
                3. 英文全称不确定时必须输出 null，禁止猜测；
                4. 每条释义不超过 80 字，不得编造音标；
                5. 最多输出 %d 条。
                """.formatted(limit);
        String userPrompt = "允许的术语类型：" + String.join(",", types) + "\n技术分类提示：" + source.domainHint()
                + "\n待抽取正文：\n" + truncate(source.text(), 6000);
        String raw = aiService.completeStructured(systemPrompt, userPrompt, null, userId);
        List<TechVocabVOs.ExtractedCandidateVO> items = parseAiItems(raw, source, types, limit, library);
        return items;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 结构化校验：任何越界、缺字段、疑似非英文主词的结果直接丢弃，不进入预览 */
    private List<TechVocabVOs.ExtractedCandidateVO> parseAiItems(String raw, SourceText source, List<String> types,
                                                                 int limit, Map<String, TechVocabTerm> library) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String json = raw.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json.substring(start, end + 1));
        } catch (Exception e) {
            return List.of();
        }
        JsonNode array = root.path("terms");
        if (!array.isArray()) {
            return List.of();
        }
        List<TechVocabVOs.ExtractedCandidateVO> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode node : array) {
            if (items.size() >= limit) {
                break;
            }
            String headword = text(node, "headword");
            if (headword == null || !HEADWORD_PATTERN.matcher(headword).matches()) {
                continue;
            }
            String key = normalizeKey(headword);
            if (!seen.add(key)) {
                continue;
            }
            String termType = text(node, "termType");
            termType = termType == null ? guessType(headword) : termType.toUpperCase(Locale.ROOT);
            if (!TYPES.contains(termType) || !types.contains(termType)) {
                continue;
            }
            String domain = text(node, "domain");
            domain = domain != null && DOMAIN_PATTERN.matcher(domain).matches()
                    ? domain.toUpperCase(Locale.ROOT) : source.domainHint();
            if (!DOMAINS.contains(domain)) {
                domain = source.domainHint();
            }
            String fullForm = text(node, "fullForm");
            if (fullForm != null && fullForm.length() > 300) {
                fullForm = null;
            }
            String meaningCn = text(node, "meaningCn");
            if (meaningCn != null && meaningCn.length() > 300) {
                meaningCn = meaningCn.substring(0, 300);
            }
            String definitionCn = text(node, "definitionCn");
            if (definitionCn != null && definitionCn.length() > 500) {
                definitionCn = definitionCn.substring(0, 500);
            }
            TechVocabTerm existing = library.get(key);
            items.add(new TechVocabVOs.ExtractedCandidateVO(
                    headword, termType, domain, fullForm, meaningCn, definitionCn,
                    contextOf(source.text(), headword), 55, "AI",
                    existing != null, existing == null ? null : existing.getId(),
                    existing == null ? null : existing.getHeadword()));
        }
        return items;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private String guessType(String headword) {
        if (headword.matches("[A-Z][A-Z0-9]{1,6}")) {
            return "ACRONYM";
        }
        return headword.contains(" ") ? "PHRASE" : "WORD";
    }

    /* ============================ 候选保存与审核 ============================ */

    @Transactional
    public TechVocabVOs.CandidateSaveResultVO saveCandidates(Long userId,
                                                             TechVocabDtos.CandidateSaveRequest request) {
        if (request == null || request.candidates() == null || request.candidates().isEmpty()) {
            throw new BusinessException(400, "请至少选择一个候选术语");
        }
        if (request.candidates().size() > MAX_LIMIT) {
            throw new BusinessException(400, "单次最多保存 " + MAX_LIMIT + " 条候选");
        }
        Map<String, TechVocabTerm> library = loadLibrary();
        int saved = 0;
        int skipped = 0;
        List<TechVocabCandidate> created = new ArrayList<>();
        for (TechVocabDtos.CandidateInput input : request.candidates()) {
            if (input == null || input.headword() == null || input.headword().isBlank()) {
                throw new BusinessException(400, "候选术语缺少英文名称");
            }
            String headword = input.headword().trim();
            if (!HEADWORD_PATTERN.matcher(headword).matches()) {
                throw new BusinessException(400, "候选术语格式不合法：" + headword);
            }
            String key = normalizeKey(headword);
            String sourceType = input.sourceType() == null ? "TEXT"
                    : input.sourceType().trim().toUpperCase(Locale.ROOT);
            if (!SOURCE_TYPES.contains(sourceType)) {
                throw new BusinessException(400, "候选来源类型不合法");
            }
            boolean exists = candidateMapper.selectCount(new LambdaQueryWrapper<TechVocabCandidate>()
                    .eq(TechVocabCandidate::getUserId, userId)
                    .eq(TechVocabCandidate::getNormalizedKey, key)
                    .eq(TechVocabCandidate::getSourceType, sourceType)
                    // source_id 为空时唯一索引不生效（NULL 互不冲突），必须显式按 IS NULL 去重
                    .isNull(input.sourceId() == null, TechVocabCandidate::getSourceId)
                    .eq(input.sourceId() != null, TechVocabCandidate::getSourceId, input.sourceId())
                    .ne(TechVocabCandidate::getReviewStatus, "REJECTED")) > 0;
            if (exists) {
                skipped++;
                continue;
            }
            TechVocabCandidate candidate = new TechVocabCandidate();
            candidate.setUserId(userId);
            candidate.setHeadword(headword);
            candidate.setNormalizedKey(key);
            candidate.setTermType(normalizeType(input.termType(), headword));
            candidate.setDomain(normalizeDomain(input.domain()));
            candidate.setFullForm(trimTo(input.fullForm(), 300));
            candidate.setMeaningCn(trimTo(input.meaningCn(), 300));
            candidate.setDefinitionCn(trimTo(input.definitionCn(), 1000));
            candidate.setSourceType(sourceType);
            candidate.setSourceId(input.sourceId());
            candidate.setSourceTitle(trimTo(input.sourceTitle(), 300));
            candidate.setSourceContext(trimTo(input.sourceContext(), 500));
            candidate.setConfidence(input.confidence() == null ? 50 : Math.max(0, Math.min(100, input.confidence())));
            candidate.setExtractor("AI".equalsIgnoreCase(input.extractor()) ? "AI" : "RULE");
            candidate.setReviewStatus("PENDING");
            TechVocabTerm duplicate = library.get(key);
            candidate.setDuplicateTermId(duplicate == null ? null : duplicate.getId());
            try {
                candidateMapper.insert(candidate);
                created.add(candidate);
                saved++;
            } catch (DuplicateKeyException race) {
                skipped++;
            }
        }
        return new TechVocabVOs.CandidateSaveResultVO(saved, skipped,
                created.stream().map(this::toCandidateVO).collect(Collectors.toList()));
    }

    public List<TechVocabVOs.CandidateVO> myCandidates(Long userId, String status) {
        LambdaQueryWrapper<TechVocabCandidate> wrapper = new LambdaQueryWrapper<TechVocabCandidate>()
                .eq(TechVocabCandidate::getUserId, userId);
        applyStatus(wrapper, status);
        wrapper.orderByDesc(TechVocabCandidate::getUpdateTime).orderByDesc(TechVocabCandidate::getId);
        return candidateMapper.selectList(wrapper).stream()
                .map(this::toCandidateVO).collect(Collectors.toList());
    }

    public List<TechVocabVOs.CandidateVO> adminCandidates(String status) {
        LambdaQueryWrapper<TechVocabCandidate> wrapper = new LambdaQueryWrapper<>();
        applyStatus(wrapper, status);
        wrapper.orderByDesc(TechVocabCandidate::getUpdateTime).orderByDesc(TechVocabCandidate::getId);
        return candidateMapper.selectList(wrapper).stream()
                .map(this::toCandidateVO).collect(Collectors.toList());
    }

    private void applyStatus(LambdaQueryWrapper<TechVocabCandidate> wrapper, String status) {
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!REVIEW_STATUSES.contains(normalized)) {
                throw new BusinessException(400, "候选状态不合法");
            }
            wrapper.eq(TechVocabCandidate::getReviewStatus, normalized);
        }
    }

    /**
     * 管理员审核：通过时建立正式词条（默认 REVIEWED，publish=true 才发布），拒绝时只改状态。
     * 幂等：已审核的候选再次提交返回当前状态，不重复建词条。
     */
    @Transactional
    public TechVocabVOs.CandidateVO reviewCandidate(Long candidateId, TechVocabDtos.CandidateReviewInput input) {
        TechVocabCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(404, "候选不存在");
        }
        if (!"PENDING".equals(candidate.getReviewStatus())) {
            return toCandidateVO(candidate);
        }
        boolean approve = input != null && Boolean.TRUE.equals(input.approve());
        if (!approve) {
            candidate.setReviewStatus("REJECTED");
            candidate.setReviewNote(trimTo(input == null ? null : input.reviewNote(), 500));
            candidateMapper.updateById(candidate);
            return toCandidateVO(candidate);
        }
        boolean publish = input != null && Boolean.TRUE.equals(input.publish());
        String meaningCn = input != null && input.meaningCn() != null && !input.meaningCn().isBlank()
                ? input.meaningCn().trim() : candidate.getMeaningCn();
        if (meaningCn == null || meaningCn.isBlank()) {
            throw new BusinessException(400, "审核通过前必须补充中文释义");
        }
        String domain = input != null && input.domain() != null && !input.domain().isBlank()
                ? normalizeDomain(input.domain()) : candidate.getDomain();
        TechVocabDtos.TermInput termInput = new TechVocabDtos.TermInput(
                candidate.getHeadword(), candidate.getTermType(), domain, "default",
                candidate.getFullForm(), null, null, null, null, meaningCn,
                candidate.getDefinitionCn(), null, null, null, null, null, null, null,
                publish ? "PUBLISHED" : "REVIEWED",
                "管理员审核通过的个人候选词条（来源：" + (candidate.getSourceTitle() == null
                        ? candidate.getSourceType() : candidate.getSourceTitle()) + "）",
                "释义由用户/AI 候选提供，已由管理员人工审核",
                null);
        TechVocabVOs.TermDetail detail = techVocabService.adminCreate(termInput);
        candidate.setTermId(detail.id());
        candidate.setReviewStatus("APPROVED");
        candidate.setReviewNote(trimTo(input == null ? null : input.reviewNote(), 500));
        candidateMapper.updateById(candidate);
        return toCandidateVO(candidate);
    }

    private TechVocabVOs.CandidateVO toCandidateVO(TechVocabCandidate candidate) {
        return new TechVocabVOs.CandidateVO(candidate.getId(), candidate.getHeadword(), candidate.getTermType(),
                candidate.getDomain(), candidate.getFullForm(), candidate.getMeaningCn(), candidate.getDefinitionCn(),
                candidate.getSourceType(), candidate.getSourceId(), candidate.getSourceTitle(),
                candidate.getSourceContext(), candidate.getConfidence(), candidate.getExtractor(),
                candidate.getReviewStatus(), candidate.getDuplicateTermId(), candidate.getTermId(),
                candidate.getReviewNote(), null);
    }

    private String normalizeKey(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String normalizeType(String type, String headword) {
        if (type != null) {
            String normalized = type.trim().toUpperCase(Locale.ROOT);
            if (TYPES.contains(normalized)) {
                return normalized;
            }
        }
        return guessType(headword);
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return "JAVA";
        }
        String normalized = domain.trim().toUpperCase(Locale.ROOT);
        return DOMAINS.contains(normalized) ? normalized : "JAVA";
    }

    private String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
