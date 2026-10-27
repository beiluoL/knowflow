package com.knowflow.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.coding.entity.CodeSubmission;
import com.knowflow.coding.judge.JudgeStatus;
import com.knowflow.coding.service.CodeProblemService;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.entity.SysUser;
import com.knowflow.exam.ai.ExamAiService;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.exam.engine.ExamGrader;
import com.knowflow.exam.engine.PaperComposer;
import com.knowflow.exam.engine.SemanticIdentity;
import com.knowflow.exam.entity.ExamAnswer;
import com.knowflow.exam.entity.ExamAttempt;
import com.knowflow.exam.entity.ExamPaper;
import com.knowflow.exam.entity.ExamPaperQuestion;
import com.knowflow.exam.entity.ExamTemplate;
import com.knowflow.exam.mapper.ExamAnswerMapper;
import com.knowflow.exam.mapper.ExamAttemptMapper;
import com.knowflow.exam.mapper.ExamPaperMapper;
import com.knowflow.exam.mapper.ExamPaperQuestionMapper;
import com.knowflow.exam.mapper.ExamTemplateMapper;
import com.knowflow.exam.service.ExamService;
import com.knowflow.exam.vo.ExamVos;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.LearningEventService;
import com.knowflow.service.MistakeService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考试系统服务实现。
 *
 * <p>关键安全/正确性约束：
 * <ul>
 *   <li>userId 全部来自认证上下文，试卷按 ownerUserId 隔离；</li>
 *   <li>截止时间用服务端 serverDeadline；交卷用条件 UPDATE 保证幂等与并发安全；</li>
 *   <li>未评分题 score=NULL，绝不当 0 分计入最终成绩；</li>
 *   <li>错题只在「规则判错」或「AI 已评且判定不合格」后写入，PENDING 不提前记错；</li>
 *   <li>WEAK/MISTAKE 组卷只基于真实作答/错题，样本不足直接拒绝。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamServiceImpl implements ExamService {

    private static final String MISTAKE_SOURCE = "EXAM";
    private static final String RUBRIC_VERSION = "v1";
    /** AI_RUNNING 认领僵死回收阈值：实例宕机后，超过此时长允许其他请求/实例重新认领。 */
    private static final long AI_GRADING_STALE_MINUTES = 10;

    private final com.knowflow.learning.evidence.LearningEvidenceService evidenceReceipts;
    private final ExamTemplateMapper templateMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ExamAnswerMapper answerMapper;
    private final LearningMistakeMapper mistakeMapper;
    private final PaperComposer composer;
    private final ExamAiService examAi;
    private final MistakeService mistakeService;
    private final LearningEventService learningEventService;
    private final CodeProblemService codeProblemService;
    private final ObjectMapper objectMapper;
    private final SysUserMapper sysUserMapper;
    private final com.knowflow.security.DocumentAccessPolicy documentAccess;
    private final com.knowflow.mapper.DocDocumentMapper sourceDocumentMapper;
    private final PlatformTransactionManager transactionManager;

    /** AI 评卷短事务边界（认领/落分各自独立，避免在 DB 事务内发起远程模型调用）。 */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTxTemplate() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    // ==================== 模板与组卷 ====================

    @Override
    public List<ExamVos.TemplateVO> listTemplates(boolean checkAvailability, Long userId) {
        List<ExamTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<ExamTemplate>()
                        .eq(ExamTemplate::getStatus, 1)
                        .orderByAsc(ExamTemplate::getId));
        List<ExamVos.TemplateVO> out = new ArrayList<>();
        for (ExamTemplate t : templates) {
            ExamVos.TemplateVO vo = toTemplateVO(t);
            if (checkAvailability) {
                try {
                    JsonNode spec = objectMapper.readTree(t.getSpec());
                    ExamRequests.ComposeRequest probe = new ExamRequests.ComposeRequest();
                    probe.setTopics(readStringList(spec.get("topics")));
                    probe.setTypeDistribution(readIntMap(spec.get("typeDistribution")));
                    probe.setStrategy(spec.path("strategy").asText(PaperComposer.STRATEGY_RANDOM));
                    probe.setSeed(1L);
                    PaperComposer.SelectionResult preview =
                            composer.preview(userId, probe.getTopics(), probe.getTypeDistribution(), null, probe.getStrategy());
                    vo.setAvailable(preview.sufficient);
                    vo.setShortage(preview.message);
                } catch (Exception e) {
                    vo.setAvailable(false);
                    vo.setShortage("模板规格解析失败");
                }
            } else {
                vo.setAvailable(true);
            }
            out.add(vo);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVos.PaperVO createFromTemplate(String templateKey, ExamRequests.TemplateInstantiateRequest req, Long userId) {
        ExamTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<ExamTemplate>()
                .eq(ExamTemplate::getTemplateKey, templateKey).eq(ExamTemplate::getStatus, 1));
        if (template == null) {
            throw new BusinessException(404, "试卷模板不存在或已停用: " + templateKey);
        }
        ExamRequests.ComposeRequest compose;
        try {
            JsonNode spec = objectMapper.readTree(template.getSpec());
            compose = new ExamRequests.ComposeRequest();
            compose.setTopics(readStringList(spec.get("topics")));
            compose.setTypeDistribution(readIntMap(spec.get("typeDistribution")));
            compose.setStrategy(spec.path("strategy").asText(PaperComposer.STRATEGY_BALANCED));
            compose.setDomain(template.getDomain());
            compose.setTotalScore(template.getTotalScore());
            compose.setDurationMinutes(template.getDurationMinutes());
            compose.setPassScore(template.getPassScore());
            compose.setMode(template.getMode());
            compose.setMultiPolicy(spec.path("multiPolicy").asText(ExamGrader.POLICY_PARTIAL_CREDIT));
        } catch (Exception e) {
            throw new BusinessException(500, "模板规格损坏: " + templateKey);
        }
        compose.setName(template.getName());
        long seed = req != null && req.getSeed() != null ? req.getSeed() : System.nanoTime();
        compose.setSeed(seed);
        if (req != null) {
            if (req.getMode() != null && !req.getMode().isBlank()) {
                compose.setMode(req.getMode());
            }
            if (req.getDurationMinutes() != null && req.getDurationMinutes() > 0) {
                compose.setDurationMinutes(req.getDurationMinutes());
            }
            if (req.getShuffleQuestion() != null) {
                compose.setShuffleQuestion(req.getShuffleQuestion());
            }
            if (req.getShuffleOption() != null) {
                compose.setShuffleOption(req.getShuffleOption());
            }
            if (req.getMultiPolicy() != null && !req.getMultiPolicy().isBlank()) {
                compose.setMultiPolicy(req.getMultiPolicy());
            }
        }
        return persistComposedPaper(compose, "TEMPLATE", template.getTemplateKey(), null, userId);
    }

    @Override
    public ExamVos.BankAvailabilityVO availability(ExamRequests.ComposeRequest req, Long userId) {
        Map<String, Integer> requested = req.getTypeDistribution() == null ? Map.of() : req.getTypeDistribution();
        PaperComposer.SelectionResult preview = composer.preview(
                userId, req.getTopics(), requested, req.getDifficulties(),
                req.getStrategy() == null ? PaperComposer.STRATEGY_RANDOM : req.getStrategy(), req.getIncludeAnswered());
        ExamVos.BankAvailabilityVO vo = new ExamVos.BankAvailabilityVO();
        // 总量按所有题型过滤后计算
        vo.setTotal(preview.available.values().stream().mapToInt(Integer::intValue).sum());
        vo.setByType(preview.available);
        vo.setRequested(preview.requested);
        vo.setAvailable(preview.available);
        vo.setSufficient(preview.sufficient);
        vo.setMessage(preview.message);
        vo.setByTopic(loadTopicCounts(userId, req.getTopics()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVos.PaperVO compose(ExamRequests.ComposeRequest req, Long userId) {
        normalizeComposeDefaults(req, userId);
        return persistComposedPaper(req, "SMART", null, null, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVos.PaperVO composeAi(ExamRequests.AiComposeRequest req, Long userId) {
        if (req == null || req.getCount() == null || req.getCount() <= 0) {
            throw new BusinessException(400, "题目数量不合法");
        }
        int count = Math.min(req.getCount(), 30);
        boolean fallbackEnabled = req.getFallbackToBank() == null || req.getFallbackToBank() != 0;
        Map<String, Integer> dist = req.getTypeDistribution();
        String[] errorOut = new String[1];
        List<ExamAiService.AiQuestion> generated = examAi.generateQuestions(
                req.getTopic(), count, dist, req.getDifficulties(), userId, req.getModel(), errorOut);
        if (errorOut[0] != null) {
            if (fallbackEnabled) {
                return fallbackToRuleCompose(req, count, errorOut[0], userId);
            }
            throw new BusinessException(422, "AI 出卷失败：" + errorOut[0]);
        }
        // 逐题 Schema 校验已在 AI 服务内完成；这里再与题库做语义去重与卷内去重
        Set<String> bankFingerprints = loadBankFingerprints();
        Set<String> used = new HashSet<>();
        List<ExamAiService.AiQuestion> accepted = new ArrayList<>();
        List<String> rejectedReasons = new ArrayList<>();
        for (ExamAiService.AiQuestion q : generated) {
            if (!q.valid()) {
                rejectedReasons.add("《" + q.title() + "》" + q.invalidReason());
                continue;
            }
            String fp = SemanticIdentity.fingerprint(q.questionType(), q.title(), q.content());
            if (bankFingerprints.contains(fp) || !used.add(fp)) {
                rejectedReasons.add("《" + q.title() + "》与已有题目语义重复");
                continue;
            }
            accepted.add(q);
        }
        if (accepted.size() < count) {
            if (fallbackEnabled) {
                return fallbackToRuleCompose(req, count,
                        "AI_VALIDATION_SHORTAGE（需要 " + count + " 题，校验/去重后仅 " + accepted.size() + " 题）",
                        userId);
            }
            throw new BusinessException(409,
                    "AI 生成题目有效数量不足：需要 " + count + " 题，校验/去重后仅 " + accepted.size()
                            + " 题（" + String.join("；", rejectedReasons.stream().limit(5).toList())
                            + "）。请降低题量或更换知识点后重试，系统不会把不合格题目塞进试卷。");
        }

        int duration = req.getDurationMinutes() == null || req.getDurationMinutes() <= 0
                ? Math.max(10, count * 2) : req.getDurationMinutes();
        int totalScore = req.getTotalScore() == null || req.getTotalScore() <= 0 ? 100 : req.getTotalScore();
        int passScore = req.getPassScore() == null ? 60 : req.getPassScore();
        String mode = req.getMode() == null ? "EXAM" : req.getMode();
        String policy = req.getMultiPolicy() == null ? ExamGrader.POLICY_PARTIAL_CREDIT : req.getMultiPolicy();

        ExamPaper paper = new ExamPaper();
        paper.setName((req.getName() == null || req.getName().isBlank())
                ? "AI 试卷 · " + (req.getTopic() == null ? "综合" : req.getTopic()) : req.getName());
        paper.setDomain("AI");
        paper.setSource("AI");
        paper.setStrategy("AI_GENERATE");
        paper.setSeedValue(null);
        paper.setDurationMinutes(duration);
        paper.setTotalScore(totalScore);
        paper.setPassScore(passScore);
        paper.setMode(mode);
        paper.setShuffleQuestion(req.getShuffleQuestion() == null ? 0 : req.getShuffleQuestion());
        paper.setShuffleOption(req.getShuffleOption() == null ? 0 : req.getShuffleOption());
        paper.setAllowPause(1);
        paper.setAllowChange(1);
        paper.setReviewStatus("PENDING_REVIEW");
        paper.setOwnerUserId(userId);
        paper.setStatus(1);
        paper.setTopics(req.getTopic());
        Map<String, Object> aiMeta = new LinkedHashMap<>();
        aiMeta.put("model", req.getModel() == null ? "default" : req.getModel());
        aiMeta.put("promptVersion", ExamAiService.PROMPT_PAPER_VERSION);
        aiMeta.put("sourceTopic", req.getTopic());
        aiMeta.put("generatedAt", LocalDateTime.now().toString());
        aiMeta.put("reviewStatus", "PENDING_REVIEW");
        try {
            paper.setAiMeta(objectMapper.writeValueAsString(aiMeta));
        } catch (Exception ignored) {
            // meta 失败不阻断
        }
        paperMapper.insert(paper);

        int base = totalScore / count;
        int remainder = totalScore - base * count;
        Set<String> topics = new HashSet<>();
        for (int i = 0; i < accepted.size(); i++) {
            ExamAiService.AiQuestion q = accepted.get(i);
            ExamPaperQuestion pq = new ExamPaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(null);
            pq.setOrdinal(i + 1);
            pq.setScore(base + (i < remainder ? 1 : 0));
            pq.setQuestionType(q.questionType());
            pq.setTopic(q.topic() == null ? "" : q.topic().toUpperCase(Locale.ROOT));
            pq.setScoringRule(policy);
            topics.add(pq.getTopic());
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", null);
            snap.put("title", q.title());
            snap.put("content", q.content());
            snap.put("questionType", q.questionType());
            snap.put("options", q.options() == null ? List.of() : q.options());
            snap.put("answer", q.answer());
            snap.put("explanation", q.explanation());
            snap.put("difficulty", q.difficulty());
            try {
                pq.setSnapshot(objectMapper.writeValueAsString(snap));
            } catch (Exception e) {
                throw new BusinessException(500, "AI 题目快照序列化失败");
            }
            paperQuestionMapper.insert(pq);
        }
        paper.setTopics(String.join(",", topics));
        paperMapper.updateById(paper);
        ExamVos.PaperVO vo = toPaperVO(paper, accepted.size());
        vo.setAiFallback(false);
        return vo;
    }

    /**
     * Phase 3H：AI 出卷失败或候选题质量不足时的确定性兜底（4.1 降级）。
     *
     * <p>硬约束：保持用户原始的题型/难度/数量要求，不偷降难度、不伪造新题；
     * 题库数量不足时由 {@link PaperComposer} 抛出 409 明确提示。
     * 试卷来源为已审核题库（reviewStatus=APPROVED），前端依据 aiFallback/fallbackReason
     * 明确标注“已切换为题库组卷”。
     */
    private ExamVos.PaperVO fallbackToRuleCompose(ExamRequests.AiComposeRequest req, int count,
                                                  String reason, Long userId) {
        ExamRequests.ComposeRequest rule = new ExamRequests.ComposeRequest();
        rule.setName(req.getName());
        rule.setDomain("JAVA");
        rule.setTopics(req.getTopic() == null || req.getTopic().isBlank()
                ? null : new ArrayList<>(List.of(req.getTopic())));
        rule.setStrategy(PaperComposer.STRATEGY_RANDOM);
        rule.setSeed(req.getSeed() == null ? System.nanoTime() : req.getSeed());
        rule.setQuestionCount(count);
        rule.setTypeDistribution(req.getTypeDistribution() != null && !req.getTypeDistribution().isEmpty()
                ? req.getTypeDistribution() : scaleTypeDistribution(count));
        rule.setDifficulties(req.getDifficulties());
        rule.setIncludeAnswered(req.getIncludeAnswered());
        rule.setTotalScore(req.getTotalScore());
        rule.setDurationMinutes(req.getDurationMinutes());
        rule.setPassScore(req.getPassScore());
        rule.setMode(req.getMode());
        rule.setShuffleQuestion(req.getShuffleQuestion());
        rule.setShuffleOption(req.getShuffleOption());
        rule.setMultiPolicy(req.getMultiPolicy());
        // 与确定性组卷同一套默认值归一化（时长/总分/及格分/模式），避免降级路径落入非空约束
        normalizeComposeDefaults(rule, userId);
        ExamVos.PaperVO vo = persistComposedPaper(rule, "SMART", null, null, userId);
        vo.setAiFallback(true);
        vo.setFallbackReason(reason);
        return vo;
    }

    /**
     * AI 未指定题型分布时，按默认权重 4:2:2:1:1（单/判/多/填/简）用最大余数法
     * 缩放到目标题量；题量小于 5 时优先给单选。
     */
    private Map<String, Integer> scaleTypeDistribution(int count) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put(QuizQuestion.TYPE_SINGLE_CHOICE, 4);
        weights.put(QuizQuestion.TYPE_TRUE_FALSE, 2);
        weights.put(QuizQuestion.TYPE_MULTIPLE_CHOICE, 2);
        weights.put(QuizQuestion.TYPE_FILL_BLANK, 1);
        weights.put(QuizQuestion.TYPE_SHORT_ANSWER, 1);
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Double> fractions = new LinkedHashMap<>();
        int assigned = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            double exact = count * e.getValue() / 10.0;
            int floor = (int) Math.floor(exact);
            result.put(e.getKey(), floor);
            fractions.put(e.getKey(), exact - floor);
            assigned += floor;
        }
        int remaining = count - assigned;
        List<String> ordered = new ArrayList<>(weights.keySet());
        ordered.sort((a, b) -> Double.compare(fractions.get(b), fractions.get(a)));
        int idx = 0;
        while (remaining > 0) {
            String type = ordered.get(idx % ordered.size());
            result.merge(type, 1, Integer::sum);
            remaining--;
            idx++;
        }
        // 移除 0 数量的题型，避免向组卷器传无意义条目
        result.values().removeIf(v -> v <= 0);
        return result;
    }

    private ExamVos.PaperVO persistComposedPaper(ExamRequests.ComposeRequest req, String source,
                                                 String templateKey, Long ownerUserId, Long userId) {
        PaperComposer.SelectionResult result = composer.compose(userId, req);
        if (!result.sufficient) {
            throw new BusinessException(409, result.message);
        }
        ExamPaper paper = new ExamPaper();
        paper.setName(req.getName() == null || req.getName().isBlank() ? "智能试卷 " + LocalDate.now() : req.getName());
        paper.setDomain(req.getDomain() == null ? "JAVA" : req.getDomain());
        paper.setSource(source);
        paper.setTemplateKey(templateKey);
        paper.setStrategy(req.getStrategy() == null ? PaperComposer.STRATEGY_RANDOM : req.getStrategy());
        paper.setSeedValue(req.getSeed());
        paper.setDurationMinutes(req.getDurationMinutes());
        paper.setTotalScore(req.getTotalScore());
        paper.setPassScore(req.getPassScore() == null ? 60 : req.getPassScore());
        paper.setMode(req.getMode() == null ? "EXAM" : req.getMode());
        paper.setShuffleQuestion(req.getShuffleQuestion() == null ? 0 : req.getShuffleQuestion());
        paper.setShuffleOption(req.getShuffleOption() == null ? 0 : req.getShuffleOption());
        paper.setAllowPause(req.getAllowPause() == null ? 1 : req.getAllowPause());
        paper.setAllowChange(req.getAllowChange() == null ? 1 : req.getAllowChange());
        paper.setBankVersion(result.bankVersion);
        paper.setReviewStatus("APPROVED");
        // A template's public specification cannot make private source snapshots public.
        // Keep legacy public papers public when every selected source is anonymously readable.
        if (ownerUserId == null && result.selected.stream().anyMatch(selected ->
                !documentAccess.canReadCategory(selected.question.getCategoryId(), null)
                        || (selected.question.getDocId() != null
                        && !documentAccess.canRead(
                                sourceDocumentMapper.selectById(selected.question.getDocId()), null)))) {
            ownerUserId = userId;
        }
        paper.setOwnerUserId(ownerUserId);
        paper.setStatus(1);
        paper.setTopics(String.join(",", result.topicsUsed.stream().sorted().toList()));
        try {
            paper.setConfigJson(objectMapper.writeValueAsString(Map.of(
                    "typeDistribution", result.requested,
                    "topics", req.getTopics() == null ? List.of() : req.getTopics(),
                    "difficulties", req.getDifficulties() == null ? List.of() : req.getDifficulties(),
                    "multiPolicy", req.getMultiPolicy() == null ? ExamGrader.POLICY_PARTIAL_CREDIT : req.getMultiPolicy(),
                    "ruleVersion", result.ruleVersion,
                    "includeAnswered", !Boolean.FALSE.equals(req.getIncludeAnswered()))));
        } catch (Exception ignored) {
            // config 仅审计用途
        }
        paperMapper.insert(paper);

        String policy = req.getMultiPolicy() == null ? ExamGrader.POLICY_PARTIAL_CREDIT : req.getMultiPolicy();
        for (PaperComposer.SelectedQuestion sq : result.selected) {
            ExamPaperQuestion pq = new ExamPaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(sq.question.getId());
            pq.setOrdinal(sq.ordinal);
            pq.setScore(sq.score);
            pq.setQuestionType(sq.question.getQuestionType());
            pq.setTopic(sq.topic);
            pq.setScoringRule(policy);
            pq.setSnapshot(composer.buildSnapshot(sq.question));
            paperQuestionMapper.insert(pq);
        }
        return toPaperVO(paper, result.selected.size());
    }

    private void normalizeComposeDefaults(ExamRequests.ComposeRequest req, Long userId) {
        if (req.getStrategy() == null || req.getStrategy().isBlank()) {
            req.setStrategy(PaperComposer.STRATEGY_RANDOM);
        }
        if (req.getSeed() == null) {
            req.setSeed(System.nanoTime());
        }
        if (req.getQuestionCount() == null || req.getQuestionCount() <= 0) {
            req.setQuestionCount(req.getTypeDistribution() == null
                    ? 10 : req.getTypeDistribution().values().stream().mapToInt(Integer::intValue).sum());
        }
        if (req.getTypeDistribution() == null || req.getTypeDistribution().isEmpty()) {
            Map<String, Integer> def;
            if (PaperComposer.STRATEGY_MISTAKE.equals(req.getStrategy())) {
                // 错题强化卷：按错题池真实题型构成分布；无可用错题时回退默认分布，
                // 由 PaperComposer 的 MISTAKE 早返回给出「没有可用错题」的明确提示
                Map<String, Integer> byMistakes = composer.mistakeTypeDistribution(userId, req.getTopics());
                def = byMistakes.isEmpty() ? defaultTypeDistribution() : byMistakes;
            } else {
                def = defaultTypeDistribution();
            }
            req.setTypeDistribution(def);
        }
        if (req.getTotalScore() == null || req.getTotalScore() <= 0) {
            req.setTotalScore(100);
        }
        if (req.getDurationMinutes() == null || req.getDurationMinutes() <= 0) {
            req.setDurationMinutes(30);
        }
        if (req.getMode() == null || req.getMode().isBlank()) {
            req.setMode("EXAM");
        }
    }

    /** 默认题型分布：4 单选 / 2 判断 / 2 多选 / 1 填空 / 1 简答。 */
    private Map<String, Integer> defaultTypeDistribution() {
        Map<String, Integer> def = new LinkedHashMap<>();
        def.put(QuizQuestion.TYPE_SINGLE_CHOICE, 4);
        def.put(QuizQuestion.TYPE_TRUE_FALSE, 2);
        def.put(QuizQuestion.TYPE_MULTIPLE_CHOICE, 2);
        def.put(QuizQuestion.TYPE_FILL_BLANK, 1);
        def.put(QuizQuestion.TYPE_SHORT_ANSWER, 1);
        return def;
    }

    // ==================== 考试进行 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVos.AttemptVO startAttempt(Long paperId, Long userId) {
        ExamPaper paper = getAccessiblePaper(paperId, userId);
        // 同用户行锁串行化「双设备同时开考」：锁内复查，保证同卷至多一个 IN_PROGRESS。
        // 不依赖唯一索引（IN_PROGRESS 是状态子集，历史已交卷尝试允许多行），也不锁整张试卷影响他人。
        sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .last("FOR UPDATE"));
        // 恢复未交卷尝试（一场考试只能有一个 IN_PROGRESS）
        ExamAttempt existing = attemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getPaperId, paperId)
                .eq(ExamAttempt::getUserId, userId)
                .eq(ExamAttempt::getStatus, ExamAttempt.STATUS_IN_PROGRESS)
                .last("LIMIT 1"));
        if (existing != null) {
            return buildAttemptVO(existing, paper);
        }
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPaperId(paperId);
        attempt.setUserId(userId);
        attempt.setMode(paper.getMode());
        attempt.setStatus(ExamAttempt.STATUS_IN_PROGRESS);
        attempt.setStartTime(LocalDateTime.now());
        attempt.setServerDeadline(LocalDateTime.now().plusMinutes(paper.getDurationMinutes()));
        int qCount = paperQuestionMapper.selectCount(new LambdaQueryWrapper<ExamPaperQuestion>()
                .eq(ExamPaperQuestion::getPaperId, paperId)).intValue();
        attempt.setQuestionCount(qCount);
        attempt.setPaperMaxScore(paper.getTotalScore());
        attempt.setCorrectCount(0);
        attempt.setGradedCount(0);
        attempt.setGradingStatus("NONE");
        attemptMapper.insert(attempt);
        return buildAttemptVO(attempt, paper);
    }

    @Override
    public ExamVos.AttemptVO getAttempt(Long attemptId, Long userId) {
        ExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        ExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        return buildAttemptVO(attempt, paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVos.AttemptVO saveDraft(Long attemptId, ExamRequests.SaveDraftRequest req, Long userId) {
        ExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        if (!ExamAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BusinessException(409, "当前考试状态为 " + attempt.getStatus() + "，不能再保存草稿");
        }
        ExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        if (LocalDateTime.now().isAfter(attempt.getServerDeadline())) {
            throw new BusinessException(409, "已超过服务端考试截止时间，草稿不再保存，请直接交卷");
        }
        long revision = draftRevision(attempt.getDraft());
        long expected = req.getExpectedRevision() == null ? 0 : req.getExpectedRevision();
        if (expected < 0) throw new BusinessException(400,"草稿版本无效");
        if (expected != revision) throw new BusinessException(409,"草稿已在其他页面更新，请读取服务端草稿后继续");
        String draftJson;
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("revision", Math.addExact(revision,1));
            data.put("answers", req.getAnswers() == null ? Map.of() : req.getAnswers());
            data.put("marked", req.getMarked() == null ? List.of() : req.getMarked());
            draftJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException(500, "草稿序列化失败");
        }
        // 只更新 draft 列并带状态条件：并发交卷把状态改为 GRADING/GRADED 后，
        // 草稿保存既不能回写 status，也不能覆盖交卷数据（0 行即拒绝）。
        LambdaUpdateWrapper<ExamAttempt> save = new LambdaUpdateWrapper<ExamAttempt>()
                .eq(ExamAttempt::getId, attemptId)
                .eq(ExamAttempt::getUserId,userId)
                .eq(ExamAttempt::getStatus, ExamAttempt.STATUS_IN_PROGRESS)
                .ge(ExamAttempt::getServerDeadline,LocalDateTime.now())
                .set(ExamAttempt::getDraft, draftJson)
                .set(ExamAttempt::getUpdateTime, LocalDateTime.now());
        if (attempt.getDraft() == null) save.isNull(ExamAttempt::getDraft); else save.eq(ExamAttempt::getDraft,attempt.getDraft());
        int rows = attemptMapper.update(null,save);
        if (rows == 0) {
            throw new BusinessException(409, "草稿版本、考试状态或截止时间已变化，草稿未保存");
        }
        return buildAttemptVO(attemptMapper.selectById(attemptId), paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVos.ResultVO submit(Long attemptId, ExamRequests.SubmitRequest req, Long userId) {
        ExamAttempt attempt = getOwnedAttemptForUpdate(attemptId, userId);
        // 幂等：已交卷直接回放已有成绩
        if (!ExamAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            return buildResultVO(attempt);
        }
        if (req != null && !LocalDateTime.now().isAfter(attempt.getServerDeadline())) {
            long revision = draftRevision(attempt.getDraft());
            if (req.getExpectedRevision() != null) {
                if (req.getExpectedRevision() < 0) throw new BusinessException(400,"草稿版本无效");
                if (req.getExpectedRevision() != revision) throw new BusinessException(409,"草稿已在其他页面更新，未交卷，请先重新加载");
            } else if (revision > 0 && req.getAnswers() != null && !req.getAnswers().isEmpty()
                    && !req.getAnswers().equals(readDraftAnswers(attempt.getDraft()))) {
                // 旧客户端仅可原样交已保存答案；缺少版本不能覆盖新草稿。
                throw new BusinessException(409,"请更新页面后交卷，当前客户端未提供草稿版本");
            }
        }
        // 条件迁移：并发/重复提交只有一个请求能把 IN_PROGRESS → GRADING
        LambdaUpdateWrapper<ExamAttempt> claim = new LambdaUpdateWrapper<ExamAttempt>()
                .eq(ExamAttempt::getId, attemptId)
                .eq(ExamAttempt::getStatus, ExamAttempt.STATUS_IN_PROGRESS)
                .set(ExamAttempt::getStatus, ExamAttempt.STATUS_GRADING);
        if (attempt.getDraft() == null) claim.isNull(ExamAttempt::getDraft); else claim.eq(ExamAttempt::getDraft,attempt.getDraft());
        int claimed = attemptMapper.update(null,claim);
        if (claimed == 0) {
            ExamAttempt changed = getOwnedAttempt(attemptId, userId);
            if (ExamAttempt.STATUS_IN_PROGRESS.equals(changed.getStatus())) throw new BusinessException(409,"草稿已更新，未交卷，请重新加载");
            return buildResultVO(changed);
        }
        return finalizeAttemptAfterClaim(attemptId, userId, req);
    }

    /**
     * 认领成功后的评分核心（手动交卷与超时自动交卷共用）。
     * 调用前必须已通过条件 UPDATE 把状态从 IN_PROGRESS 迁移到 GRADING。
     */
    private ExamVos.ResultVO finalizeAttemptAfterClaim(Long attemptId, Long userId,
                                                       ExamRequests.SubmitRequest req) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);

        List<ExamPaperQuestion> questions = listPaperQuestions(attempt.getPaperId());
        Map<Long, String> draftAnswers = readDraftAnswers(attempt.getDraft());
        boolean deadlineExpired = LocalDateTime.now().isAfter(attempt.getServerDeadline());
        // 服务端截止时间为准：超时后交卷只采信截止前自动保存的草稿，请求体内的新答案不生效；
        // 超时自动交卷（req=null）同样走 draft-only 路径。
        boolean useRequestBody = !deadlineExpired && req != null;
        Map<Long, String> submitted = useRequestBody && req.getAnswers() != null
                ? req.getAnswers() : Map.of();
        Map<Long, Integer> timeCosts = useRequestBody && req.getTimeCosts() != null
                ? req.getTimeCosts() : Map.of();
        List<Integer> marked = useRequestBody && req.getMarked() != null
                ? req.getMarked() : List.of();

        int totalScore = 0;
        int maxScore = 0;
        int correctCount = 0;
        int gradedCount = 0;
        boolean pendingAi = false;
        boolean pendingJudge = false;

        for (ExamPaperQuestion pq : questions) {
            String userAnswer = submitted.getOrDefault(pq.getId(), draftAnswers.getOrDefault(pq.getId(), ""));
            if (userAnswer == null) {
                userAnswer = "";
            }
            JsonNode snap = readSnapshot(pq.getSnapshot());
            String standard = snap.path("answer").asText("");

            ExamAnswer answer = new ExamAnswer();
            answer.setAttemptId(attemptId);
            answer.setPaperQuestionId(pq.getId());
            answer.setQuestionId(pq.getQuestionId());
            answer.setOrdinal(pq.getOrdinal());
            answer.setTopic(pq.getTopic());
            answer.setUserAnswer(userAnswer);
            answer.setMaxScore(pq.getScore());
            answer.setTimeCost(timeCosts.getOrDefault(pq.getId(), 0));

            boolean answerInserted = false;
            if (ExamGrader.requiresAi(pq.getQuestionType())) {
                answer.setGraded(0);
                answer.setGradeType(ExamAnswer.GRADE_PENDING);
                answer.setScore(null);
                pendingAi = true;
            } else if (ExamGrader.isCode(pq.getQuestionType())) {
                // Phase 3G：CODE 题不走规则、不走 AI，走代码判题网关
                if (userAnswer.isBlank()) {
                    // 未作答是用户事实：0 分，已评；不触发判题、不记错（与客观题空答案口径一致）
                    answer.setGraded(1);
                    answer.setGradeType(ExamAnswer.GRADE_RULE);
                    answer.setScore(0);
                    answer.setIsCorrect(0);
                    maxScore += pq.getScore();
                    gradedCount++;
                } else {
                    answer.setGraded(0);
                    answer.setGradeType(ExamAnswer.GRADE_JUDGE);
                    answer.setScore(null);
                    answerMapper.insert(answer);
                    answerInserted = true;
                    CodeSubmission sub = safeJudgeCode(userId, pq.getQuestionId(), userAnswer, answer.getId());
                    boolean finished = sub != null && sub.getScore() != null
                            && JudgeStatus.isFinished(sub.getJudgeStatus());
                    if (finished) {
                        int scaled = scaleCodeScore(sub.getScore(), pq.getScore());
                        boolean correct = JudgeStatus.ACCEPTED.equals(sub.getJudgeStatus());
                        Map<String, Object> detail = new LinkedHashMap<>();
                        detail.put("submissionId", sub.getId());
                        detail.put("judgeStatus", sub.getJudgeStatus());
                        detail.put("passed", sub.getPassedCount());
                        detail.put("total", sub.getTotalCount());
                        detail.put("judgeDetail", sub.getJudgeDetail());
                        int rows = answerMapper.update(null, new LambdaUpdateWrapper<ExamAnswer>()
                                .eq(ExamAnswer::getId, answer.getId())
                                .eq(ExamAnswer::getGraded, 0)
                                .set(ExamAnswer::getGraded, 1)
                                .set(ExamAnswer::getGradeType, ExamAnswer.GRADE_JUDGE)
                                .set(ExamAnswer::getScore, scaled)
                                .set(ExamAnswer::getIsCorrect, correct ? 1 : 0)
                                .set(ExamAnswer::getGradeDetail, writeJsonSafe(detail))
                                .set(ExamAnswer::getUpdateTime, LocalDateTime.now()));
                        if (rows > 0) {
                            evidenceReceipts.captureRequired(userId, "EXAM_ANSWER", answer.getId());
                            totalScore += scaled;
                            maxScore += pq.getScore();
                            gradedCount++;
                            if (correct) {
                                correctCount++;
                            } else if (JudgeStatus.isUserFailure(sub.getJudgeStatus())) {
                                // System Error / 判题不可用不记错；只有用户失败才归集
                                collectCodeMistake(pq, snap, sub, userId);
                            }
                            recordAnswerEvent(pq, correct, scaled, userId);
                        }
                    } else {
                        // 平台失败/判题服务未就绪：score 保持 NULL，可通过重判接口重试，绝不默认 0 分
                        pendingJudge = true;
                    }
                }
            } else {
                ExamGrader.GradeOutcome outcome = ExamGrader.grade(
                        pq.getQuestionType(), standard, userAnswer, pq.getScoringRule(), pq.getScore());
                if (outcome.pending()) {
                    answer.setGraded(0);
                    answer.setGradeType(ExamAnswer.GRADE_PENDING);
                    answer.setScore(null);
                    pendingAi = true;
                } else {
                    answer.setGraded(1);
                    answer.setGradeType(ExamAnswer.GRADE_RULE);
                    answer.setScore(outcome.score());
                    answer.setIsCorrect(outcome.correct() ? 1 : 0);
                    try {
                        answer.setGradeDetail(objectMapper.writeValueAsString(outcome.detail()));
                    } catch (Exception ignored) {
                        // detail 仅审计
                    }
                    totalScore += outcome.score();
                    maxScore += pq.getScore();
                    gradedCount++;
                    if (outcome.correct()) {
                        correctCount++;
                    } else if (!userAnswer.isBlank()) {
                        // 空答案未作答不记错题，避免污染错题本
                        collectMistake(pq, snap, userAnswer, userId);
                    }
                    recordAnswerEvent(pq, outcome.correct(), outcome.score(), userId);
                }
            }
            if (!answerInserted) {
                answerMapper.insert(answer);
            }
            if (Integer.valueOf(1).equals(answer.getGraded()) && answer.getScore() != null) {
                evidenceReceipts.captureRequired(userId, "EXAM_ANSWER", answer.getId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        attempt.setSubmitTime(now);
        long seconds = Duration.between(attempt.getStartTime(), now).getSeconds();
        long limit = paperDurationSeconds(attempt.getPaperId());
        attempt.setDurationSeconds((int) Math.max(0, Math.min(seconds, limit)));
        attempt.setTotalScore(totalScore);
        attempt.setMaxScore(maxScore);
        attempt.setCorrectCount(correctCount);
        attempt.setGradedCount(gradedCount);
        attempt.setQuestionCount(questions.size());
        // AI 与代码判题是两条独立结算链：AI 优先展示，AI 完成后若仍有待判代码题会回落到 PENDING_JUDGE
        attempt.setGradingStatus(pendingAi ? ExamAttempt.GRADING_PENDING_AI
                : pendingJudge ? ExamAttempt.GRADING_PENDING_JUDGE
                : ExamAttempt.GRADING_NONE);
        attempt.setStatus(pendingAi || pendingJudge ? ExamAttempt.STATUS_GRADING : ExamAttempt.STATUS_GRADED);
        attempt.setClientVersion(req == null ? "AUTO_TIMEOUT" : req.getClientVersion());
        try {
            attempt.setMarked(objectMapper.writeValueAsString(marked));
        } catch (Exception ignored) {
            //
        }
        attemptMapper.updateById(attempt);
        return buildResultVO(attempt);
    }

    @Override
    public int autoSubmitExpiredAttempts(int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 200));
        List<ExamAttempt> expired = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getStatus, ExamAttempt.STATUS_IN_PROGRESS)
                .lt(ExamAttempt::getServerDeadline, LocalDateTime.now())
                .orderByAsc(ExamAttempt::getServerDeadline)
                .last("LIMIT " + limit));
        int done = 0;
        for (ExamAttempt a : expired) {
            try {
                // 每条尝试独立条件认领：与用户手动交卷竞争时只可能有一方成功（多实例部署同样安全）。
                int claimed = attemptMapper.update(null, new LambdaUpdateWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getId, a.getId())
                        .eq(ExamAttempt::getStatus, ExamAttempt.STATUS_IN_PROGRESS)
                        .lt(ExamAttempt::getServerDeadline, LocalDateTime.now())
                        .set(ExamAttempt::getStatus, ExamAttempt.STATUS_GRADING)
                        .set(ExamAttempt::getUpdateTime, LocalDateTime.now()));
                if (claimed == 0) {
                    continue;
                }
                txTemplate.execute(status -> {
                    finalizeAttemptAfterClaim(a.getId(), a.getUserId(), null);
                    return null;
                });
                done++;
            } catch (Exception e) {
                log.warn("auto-submit expired attempt failed, attemptId={}", a.getId(), e);
            }
        }
        return done;
    }

    @Override
    public ExamVos.AiGradeResultVO runAiGrading(Long attemptId, Long userId) {
        ExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        ExamVos.AiGradeResultVO out = new ExamVos.AiGradeResultVO();
        out.setAttemptId(attemptId);
        boolean aiAvailable = examAi.isConfigured();
        out.setAiAvailable(aiAvailable);
        String gradingStatus = attempt.getGradingStatus();
        if (!ExamAttempt.GRADING_PENDING_AI.equals(gradingStatus)
                && !ExamAttempt.GRADING_AI_FAILED.equals(gradingStatus)
                && !ExamAttempt.GRADING_AI_RUNNING.equals(gradingStatus)) {
            out.setStatus(attempt.getStatus());
            out.setGradingStatus(gradingStatus);
            out.setGradedAi(0);
            out.setPending(0);
            out.setMessage("没有待 AI 评分的题目");
            return out;
        }
        if (!aiAvailable) {
            out.setStatus(attempt.getStatus());
            out.setGradingStatus(gradingStatus);
            out.setMessage("当前未配置可用模型，主观题保持待评状态，不会按 0 分处理。配置模型后可重新发起 AI 辅助评分。");
            out.setGradedAi(0);
            out.setPending(listPendingAnswers(attemptId).size());
            return out;
        }

        // ① 条件认领（独立短事务，提交即对其他线程/实例可见）：
        //    PENDING_AI / AI_FAILED → AI_RUNNING；僵死 AI_RUNNING（超过 10 分钟，
        //    如实例宕机）可回收，进行中的 AI_RUNNING 被拒绝，保证并发补评只跑一次。
        if (!claimAiGrading(attemptId)) {
            throw new BusinessException(409, "AI 评卷正在进行中或状态已变更，请稍后查看结果，勿重复发起");
        }

        // ② 模型调用放在 DB 事务之外，避免长事务持锁占用连接。
        List<ExamAnswer> claimedPending = listPendingAnswers(attemptId);
        List<ExamAiService.AiGrade> grades;
        try {
            Map<Long, ExamPaperQuestion> pqMap = paperQuestionMap(attempt.getPaperId());
            List<ExamAiService.GradeInput> inputs = new ArrayList<>();
            for (ExamAnswer a : claimedPending) {
                ExamPaperQuestion pq = pqMap.get(a.getPaperQuestionId());
                if (pq == null) {
                    continue;
                }
                JsonNode snap = readSnapshot(pq.getSnapshot());
                inputs.add(new ExamAiService.GradeInput(
                        a.getPaperQuestionId(),
                        snap.path("title").asText(""),
                        snap.path("content").asText(""),
                        pq.getQuestionType(),
                        snap.path("answer").asText(""),
                        a.getUserAnswer() == null ? "" : a.getUserAnswer()));
            }
            grades = examAi.gradeSubjective(inputs, userId, null);
        } catch (RuntimeException e) {
            releaseAiGrading(attemptId);
            throw e;
        }

        // ③ 落分在独立短事务内；失败/异常释放认领，保持 PENDING 可重试（不记 0、不入错题）。
        try {
            return txTemplate.execute(status -> commitAiGrading(attemptId, claimedPending, grades));
        } catch (RuntimeException e) {
            releaseAiGrading(attemptId);
            throw e;
        }
    }

    /** AI 评卷条件认领，返回是否认领成功。 */
    private boolean claimAiGrading(Long attemptId) {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(AI_GRADING_STALE_MINUTES);
        int rows = attemptMapper.update(null, new LambdaUpdateWrapper<ExamAttempt>()
                .eq(ExamAttempt::getId, attemptId)
                .in(ExamAttempt::getStatus, ExamAttempt.STATUS_GRADING, ExamAttempt.STATUS_GRADING_FAILED)
                .and(w -> w.eq(ExamAttempt::getGradingStatus, ExamAttempt.GRADING_PENDING_AI)
                        .or().eq(ExamAttempt::getGradingStatus, ExamAttempt.GRADING_AI_FAILED)
                        .or(o -> o.eq(ExamAttempt::getGradingStatus, ExamAttempt.GRADING_AI_RUNNING)
                                .lt(ExamAttempt::getUpdateTime, staleBefore)))
                .set(ExamAttempt::getGradingStatus, ExamAttempt.GRADING_AI_RUNNING)
                .set(ExamAttempt::getUpdateTime, LocalDateTime.now()));
        return rows == 1;
    }

    /** 异常/失败路径释放认领：仍有未评分题回到 PENDING_AI，否则收尾为 AI_GRADED。 */
    private void releaseAiGrading(Long attemptId) {
        try {
            txTemplate.executeWithoutResult(status -> {
                long pendingCount = answerMapper.selectCount(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getAttemptId, attemptId)
                        .eq(ExamAnswer::getGraded, 0));
                attemptMapper.update(null, new LambdaUpdateWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getId, attemptId)
                        .eq(ExamAttempt::getGradingStatus, ExamAttempt.GRADING_AI_RUNNING)
                        .set(ExamAttempt::getGradingStatus,
                                pendingCount > 0 ? ExamAttempt.GRADING_PENDING_AI : ExamAttempt.GRADING_AI_GRADED)
                        .set(ExamAttempt::getStatus,
                                pendingCount > 0 ? ExamAttempt.STATUS_GRADING : ExamAttempt.STATUS_GRADED)
                        .set(ExamAttempt::getUpdateTime, LocalDateTime.now()));
            });
        } catch (Exception e) {
            log.warn("release AI grading claim failed, attemptId={}, stale lock will be reclaimed in {} minutes",
                    attemptId, AI_GRADING_STALE_MINUTES, e);
        }
    }

    /** 提交 AI 评分结果（事务内调用）。每题条件更新 graded=0 → 1，杜绝并发重复落分/重复错题/重复事件。 */
    private ExamVos.AiGradeResultVO commitAiGrading(Long attemptId,
                                                    List<ExamAnswer> claimedPending,
                                                    List<ExamAiService.AiGrade> grades) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        Map<Long, ExamAiService.AiGrade> byId = grades.stream()
                .collect(Collectors.toMap(ExamAiService.AiGrade::paperQuestionId, g -> g, (a, b) -> a));
        Map<Long, ExamPaperQuestion> pqMap = paperQuestionMap(attempt.getPaperId());
        Set<Long> claimedIds = claimedPending.stream()
                .map(ExamAnswer::getPaperQuestionId).collect(Collectors.toSet());

        int success = 0;
        int failed = 0;
        // 重新加载未评分行：认领后若状态已被外部改变则不评。
        for (ExamAnswer a : listPendingAnswers(attemptId)) {
            if (!claimedIds.contains(a.getPaperQuestionId())) {
                continue;
            }
            ExamAiService.AiGrade g = byId.get(a.getPaperQuestionId());
            if (g == null || !g.valid()) {
                failed++;
                continue;
            }
            ExamPaperQuestion pq = pqMap.get(a.getPaperQuestionId());
            if (pq == null) {
                failed++;
                continue;
            }
            int score = (int) Math.round(pq.getScore() * g.total100() / 100.0);
            score = Math.max(0, Math.min(pq.getScore(), score));
            boolean correct = g.total100() >= 60;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("rubricVersion", RUBRIC_VERSION);
            detail.put("promptVersion", ExamAiService.PROMPT_RUBRIC_VERSION);
            detail.put("modelTotal", g.modelTotal());
            detail.put("serverTotal", g.total100());
            detail.put("confidence", g.confidence());
            detail.put("weights", ExamAiService.RUBRIC_WEIGHTS);
            detail.put("dimensions", g.dimensions());
            detail.put("hitPoints", g.hitPoints());
            detail.put("missingPoints", g.missingPoints());
            detail.put("errors", g.errors());
            detail.put("suggestions", g.suggestions());
            detail.put("evidence", g.evidence());
            detail.put("gradedAt", LocalDateTime.now().toString());
            detail.put("note", "AI 辅助评分，非人工审核成绩");
            String detailJson;
            try {
                detailJson = objectMapper.writeValueAsString(detail);
            } catch (Exception e) {
                detailJson = null;
            }
            // 条件更新：仅 graded=0 的行可被写入，返回 0 行说明已被其他评分者处理（双保险）。
            int rows = answerMapper.update(null, new LambdaUpdateWrapper<ExamAnswer>()
                    .eq(ExamAnswer::getId, a.getId())
                    .eq(ExamAnswer::getGraded, 0)
                    .set(ExamAnswer::getGraded, 1)
                    .set(ExamAnswer::getGradeType, ExamAnswer.GRADE_AI)
                    .set(ExamAnswer::getScore, score)
                    .set(ExamAnswer::getIsCorrect, correct ? 1 : 0)
                    .set(ExamAnswer::getGradeDetail, detailJson)
                    .set(ExamAnswer::getUpdateTime, LocalDateTime.now()));
            if (rows == 0) {
                continue;
            }
            // Same short transaction as the conditional grade write; a failed receipt rolls
            // this assessment back and the existing claim-release path leaves it retryable.
            evidenceReceipts.captureRequired(attempt.getUserId(), "EXAM_ANSWER", a.getId());
            success++;

            if (!correct && a.getUserAnswer() != null && !a.getUserAnswer().isBlank()) {
                collectMistake(pq, readSnapshot(pq.getSnapshot()), a.getUserAnswer(), attempt.getUserId());
            }
            recordAnswerEvent(pq, correct, score, attempt.getUserId());
        }

        // 汇总重算（只统计已评分题；NULL 分不计）
        List<ExamAnswer> all = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getAttemptId, attemptId));
        int totalScore = all.stream().filter(a -> a.getScore() != null).mapToInt(ExamAnswer::getScore).sum();
        int maxScore = all.stream().filter(a -> a.getGraded() != null && a.getGraded() == 1)
                .mapToInt(ExamAnswer::getMaxScore).sum();
        int graded = (int) all.stream().filter(a -> a.getGraded() != null && a.getGraded() == 1).count();
        int correct = (int) all.stream().filter(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1).count();
        Map<String, Object> aiMeta = new LinkedHashMap<>();
        aiMeta.put("gradedAt", LocalDateTime.now().toString());
        aiMeta.put("gradedAi", success);
        aiMeta.put("failed", failed);
        aiMeta.put("promptVersion", ExamAiService.PROMPT_RUBRIC_VERSION);
        String aiMetaJson;
        try {
            aiMetaJson = objectMapper.writeValueAsString(aiMeta);
        } catch (Exception e) {
            aiMetaJson = null;
        }
        // 只有仍持有 AI_RUNNING 认领者可以收尾；并发/僵死回收后被他人接管则本事务放弃。
        // Phase 3G：若仍有 CODE 题等待判题，AI 评完也不能置 GRADED，回落到 PENDING_JUDGE。
        long remainJudge = all.stream()
                .filter(a -> a.getGraded() != null && a.getGraded() == 0
                        && ExamAnswer.GRADE_JUDGE.equals(a.getGradeType())).count();
        String finalGradingStatus;
        String finalStatus;
        if (failed > 0) {
            finalGradingStatus = ExamAttempt.GRADING_AI_FAILED;
            finalStatus = ExamAttempt.STATUS_GRADING_FAILED;
        } else if (remainJudge > 0) {
            finalGradingStatus = ExamAttempt.GRADING_PENDING_JUDGE;
            finalStatus = ExamAttempt.STATUS_GRADING;
        } else {
            finalGradingStatus = ExamAttempt.GRADING_AI_GRADED;
            finalStatus = ExamAttempt.STATUS_GRADED;
        }
        int rows = attemptMapper.update(null, new LambdaUpdateWrapper<ExamAttempt>()
                .eq(ExamAttempt::getId, attemptId)
                .eq(ExamAttempt::getGradingStatus, ExamAttempt.GRADING_AI_RUNNING)
                .set(ExamAttempt::getTotalScore, totalScore)
                .set(ExamAttempt::getMaxScore, maxScore)
                .set(ExamAttempt::getGradedCount, graded)
                .set(ExamAttempt::getCorrectCount, correct)
                .set(ExamAttempt::getQuestionCount, all.size())
                .set(ExamAttempt::getGradingStatus, finalGradingStatus)
                .set(ExamAttempt::getStatus, finalStatus)
                .set(ExamAttempt::getAiMeta, aiMetaJson)
                .set(ExamAttempt::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(409, "AI 评卷状态已变更，本次结果未写入");
        }

        ExamVos.AiGradeResultVO out = new ExamVos.AiGradeResultVO();
        out.setAttemptId(attemptId);
        out.setAiAvailable(true);
        out.setStatus(finalStatus);
        out.setGradingStatus(finalGradingStatus);
        out.setGradedAi(success);
        out.setPending(failed);
        out.setMessage(failed == 0
                ? "AI 已完成全部主观题辅助评分"
                : failed + " 题评分失败，保持待评，可重试；其余题目评分不受影响");
        return out;
    }

    /** 试卷题目快照映射（paperQuestionId → 题目）。 */
    private Map<Long, ExamPaperQuestion> paperQuestionMap(Long paperId) {
        return listPaperQuestions(paperId).stream()
                .collect(Collectors.toMap(ExamPaperQuestion::getId, pq -> pq, (a, b) -> a));
    }

    // ==================== 成绩 / 记录 / 错题 / Dashboard ====================

    @Override
    public ExamVos.ResultVO getResult(Long attemptId, Long userId) {
        return buildResultVO(getOwnedAttempt(attemptId, userId));
    }

    @Override
    public List<ExamVos.AttemptListItemVO> listMyAttempts(Long userId) {
        return attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getUserId, userId)
                        .orderByDesc(ExamAttempt::getCreateTime)).stream()
                .map(this::toListItem)
                .toList();
    }

    @Override
    public List<ExamVos.PaperVO> listPapers(boolean mineOnly, Long userId) {
        LambdaQueryWrapper<ExamPaper> qw = new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getStatus, 1);
        if (mineOnly) {
            qw.eq(ExamPaper::getOwnerUserId, userId);
        } else {
            qw.and(w -> w.isNull(ExamPaper::getOwnerUserId).or().eq(ExamPaper::getOwnerUserId, userId));
        }
        qw.orderByDesc(ExamPaper::getCreateTime);
        List<ExamPaper> papers = paperMapper.selectList(qw);
        Map<Long, Integer> counts = new HashMap<>();
        for (ExamPaper p : papers) {
            counts.put(p.getId(), paperQuestionMapper.selectCount(
                    new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, p.getId())).intValue());
        }
        return papers.stream().map(p -> toPaperVO(p, counts.getOrDefault(p.getId(), 0))).toList();
    }

    @Override
    public IPage<ExamVos.ExamMistakeVO> examMistakes(String topic, Integer mastered, String from, String to,
                                                     Integer pageNum, Integer pageSize, Long userId) {
        LambdaQueryWrapper<LearningMistake> qw = new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getSource, MISTAKE_SOURCE);
        if (topic != null && !topic.isBlank()) {
            qw.eq(LearningMistake::getCategory, topic.trim().toUpperCase(Locale.ROOT));
        }
        if (mastered != null) {
            qw.eq(LearningMistake::getMastered, mastered);
        }
        if (from != null && !from.isBlank()) {
            qw.ge(LearningMistake::getCreateTime, LocalDate.parse(from).atStartOfDay());
        }
        if (to != null && !to.isBlank()) {
            qw.lt(LearningMistake::getCreateTime, LocalDate.parse(to).plusDays(1).atStartOfDay());
        }
        qw.orderByDesc(LearningMistake::getCreateTime);
        IPage<LearningMistake> page = mistakeMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : Math.min(pageSize, 100)), qw);
        Page<ExamVos.ExamMistakeVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toExamMistakeVO).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMistakeMastered(Long mistakeId, Long userId) {
        // 复用错题服务的归属校验与幂等更新
        mistakeService.markMastered(mistakeId, userId);
    }

    @Override
    public ExamVos.DashboardVO dashboard(Long userId) {
        ExamVos.DashboardVO vo = new ExamVos.DashboardVO();
        List<ExamAttempt> attempts = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getUserId, userId)
                .orderByDesc(ExamAttempt::getCreateTime));
        vo.setAttemptCount(attempts.size());
        List<ExamAttempt> graded = attempts.stream()
                .filter(a -> ExamAttempt.STATUS_GRADED.equals(a.getStatus()) && a.getPaperMaxScore() != null)
                .toList();
        vo.setGradedPaperCount(graded.size());
        int avg = 0;
        if (!graded.isEmpty()) {
            avg = (int) Math.round(graded.stream()
                    .mapToInt(a -> (int) Math.round(a.getTotalScore() * 100.0 / a.getPaperMaxScore()))
                    .average().orElse(0));
        }
        vo.setAvgRate(avg);

        long mistakeCount = mistakeMapper.selectCount(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId).eq(LearningMistake::getSource, MISTAKE_SOURCE));
        long pendingMistake = mistakeMapper.selectCount(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId).eq(LearningMistake::getSource, MISTAKE_SOURCE)
                .eq(LearningMistake::getMastered, 0));
        vo.setMistakeCount((int) mistakeCount);
        vo.setPendingMistakeCount((int) pendingMistake);

        vo.setRecentAttempts(attempts.stream().limit(5).map(this::toListItem).toList());
        List<LearningMistake> recentMistakes = mistakeMapper.selectList(new LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, userId).eq(LearningMistake::getSource, MISTAKE_SOURCE)
                        .orderByDesc(LearningMistake::getCreateTime).last("LIMIT 5"));
        vo.setRecentMistakes(recentMistakes.stream().map(this::toExamMistakeVO).toList());

        vo.setWeakTopics(computeWeakTopics(userId));
        vo.setTemplates(listTemplates(true, userId));
        return vo;
    }

    // ==================== 私有装配 ====================

    private List<ExamVos.GroupAnalysis> computeWeakTopics(Long userId) {
        List<ExamAttempt> attempts = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getUserId, userId));
        if (attempts.isEmpty()) {
            return List.of();
        }
        List<Long> attemptIds = attempts.stream().map(ExamAttempt::getId).toList();
        List<ExamAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .in(ExamAnswer::getAttemptId, attemptIds)
                .eq(ExamAnswer::getGraded, 1)
                .isNotNull(ExamAnswer::getTopic));
        Map<String, int[]> agg = new LinkedHashMap<>(); // topic -> [score, max, count]
        Map<String, Integer> correct = new HashMap<>();
        for (ExamAnswer a : answers) {
            int[] arr = agg.computeIfAbsent(a.getTopic(), k -> new int[3]);
            arr[0] += a.getScore() == null ? 0 : a.getScore();
            arr[1] += a.getMaxScore();
            arr[2]++;
            if (a.getIsCorrect() != null && a.getIsCorrect() == 1) {
                correct.merge(a.getTopic(), 1, Integer::sum);
            }
        }
        List<ExamVos.GroupAnalysis> out = new ArrayList<>();
        agg.forEach((topic, arr) -> {
            if (arr[2] < 2) {
                // 样本不足不给结论
                return;
            }
            ExamVos.GroupAnalysis g = new ExamVos.GroupAnalysis();
            g.setKey(topic);
            g.setName(topic);
            g.setCount(arr[2]);
            g.setGradedCount(arr[2]);
            g.setScore(arr[0]);
            g.setMaxScore(arr[1]);
            g.setCorrectCount(correct.getOrDefault(topic, 0));
            g.setRate(arr[1] == 0 ? null : (int) Math.round(arr[0] * 100.0 / arr[1]));
            out.add(g);
        });
        return out.stream()
                .sorted(Comparator.comparingInt((ExamVos.GroupAnalysis g) -> g.getRate() == null ? 101 : g.getRate()))
                .limit(5)
                .toList();
    }

    private void collectMistake(ExamPaperQuestion pq, JsonNode snap, String userAnswer, Long userId) {
        try {
            LearningMistake m = new LearningMistake();
            String title = snap.path("title").asText("");
            String content = snap.path("content").asText("");
            m.setQuestion((title + "：" + content).replaceAll("^：", ""));
            m.setWrongAnswer(displayAnswer(pq.getQuestionType(), userAnswer, snap));
            m.setCorrectAnswer(displayAnswer(pq.getQuestionType(), snap.path("answer").asText(""), snap));
            m.setCategory(pq.getTopic());
            m.setDifficulty(snap.path("difficulty").asInt(2));
            m.setQuestionId(pq.getQuestionId());
            m.setSource(MISTAKE_SOURCE);
            mistakeService.addMistake(m, userId);
        } catch (Exception e) {
            log.warn("考试错题归集失败 attempt paperQuestionId={}: {}", pq.getId(), e.getMessage());
        }
    }

    private void recordAnswerEvent(ExamPaperQuestion pq, boolean correct, int score, Long userId) {
        try {
            Long refId = pq.getQuestionId() != null ? pq.getQuestionId() : pq.getId();
            learningEventService.record(userId, LearningEventType.QUESTION_ANSWERED, "EXAM", refId,
                    Map.of("correct", correct, "score", score, "paperQuestionId", pq.getId()));
        } catch (Exception e) {
            log.warn("学习事件记录失败 paperQuestionId={}: {}", pq.getId(), e.getMessage());
        }
    }

    // ==================== Phase 3G：CODE 题判题集成 ====================

    /** 调用代码判题网关（EXAM 上下文）。任何异常按平台失败处理：返回 null，调用方必须保持待判、不计 0。 */
    private CodeSubmission safeJudgeCode(Long userId, Long questionId, String sourceCode, Long examAnswerId) {
        try {
            return codeProblemService.judgeForContext(userId, questionId, "java", sourceCode,
                    true, CodeSubmission.CONTEXT_EXAM, examAnswerId);
        } catch (Exception e) {
            log.warn("[ExamJudge] 代码判题调用失败 examAnswerId={} questionId={}: {}",
                    examAnswerId, questionId, e.getMessage());
            return null;
        }
    }

    /** 判题百分制 → 试卷题目分值（四舍五入并夹取）。 */
    private int scaleCodeScore(Integer score100, int maxScore) {
        if (score100 == null) {
            return 0;
        }
        int s = (int) Math.round(maxScore * score100 / 100.0);
        return Math.max(0, Math.min(maxScore, s));
    }

    /** 考试代码题错题归集：只在用户失败（WA/CE/RE/TLE/MLE）时调用，System Error 不记错。 */
    private void collectCodeMistake(ExamPaperQuestion pq, JsonNode snap, CodeSubmission sub, Long userId) {
        try {
            LearningMistake m = new LearningMistake();
            m.setQuestion(snap.path("title").asText(""));
            String passed = sub.getPassedCount() == null ? "0" : String.valueOf(sub.getPassedCount());
            String total = sub.getTotalCount() == null ? "0" : String.valueOf(sub.getTotalCount());
            m.setWrongAnswer(codeStatusLabel(sub.getJudgeStatus()) + "（" + passed + "/" + total + " 用例通过）");
            m.setCorrectAnswer("通过全部公开与隐藏用例；题解在题库通过后开放");
            m.setCategory(pq.getTopic());
            m.setDifficulty(snap.path("difficulty").asInt(2));
            m.setQuestionId(pq.getQuestionId());
            m.setSource(MISTAKE_SOURCE);
            mistakeService.addMistake(m, userId);
        } catch (Exception e) {
            log.warn("考试代码错题归集失败 paperQuestionId={}: {}", pq.getId(), e.getMessage());
        }
    }

    private String codeStatusLabel(String s) {
        if (s == null) {
            return "判题未完成";
        }
        return switch (s) {
            case JudgeStatus.WRONG_ANSWER -> "答案错误";
            case JudgeStatus.COMPILE_ERROR -> "编译错误";
            case JudgeStatus.RUNTIME_ERROR -> "运行时错误";
            case JudgeStatus.TIME_LIMIT_EXCEEDED -> "运行超时";
            case JudgeStatus.MEMORY_LIMIT_EXCEEDED -> "内存超限";
            case JudgeStatus.ACCEPTED -> "通过";
            default -> s;
        };
    }

    private String writeJsonSafe(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVos.ResultVO rejudgeCodeAnswers(Long attemptId, Long userId) {
        ExamAttempt attempt = getOwnedAttempt(attemptId, userId);
        List<ExamAnswer> pending = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getAttemptId, attemptId)
                .eq(ExamAnswer::getGraded, 0)
                .eq(ExamAnswer::getGradeType, ExamAnswer.GRADE_JUDGE));
        if (pending.isEmpty()) {
            return buildResultVO(attemptMapper.selectById(attemptId));
        }
        Map<Long, ExamPaperQuestion> pqMap = paperQuestionMap(attempt.getPaperId());
        for (ExamAnswer a : pending) {
            ExamPaperQuestion pq = pqMap.get(a.getPaperQuestionId());
            if (pq == null || a.getUserAnswer() == null || a.getUserAnswer().isBlank()) {
                continue;
            }
            CodeSubmission sub = safeJudgeCode(userId, a.getQuestionId(), a.getUserAnswer(), a.getId());
            boolean finished = sub != null && sub.getScore() != null
                    && JudgeStatus.isFinished(sub.getJudgeStatus());
            if (!finished) {
                continue; // 判题仍不可用：保持待判，本次不改动
            }
            int scaled = scaleCodeScore(sub.getScore(), pq.getScore());
            boolean correct = JudgeStatus.ACCEPTED.equals(sub.getJudgeStatus());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("submissionId", sub.getId());
            detail.put("judgeStatus", sub.getJudgeStatus());
            detail.put("passed", sub.getPassedCount());
            detail.put("total", sub.getTotalCount());
            detail.put("retried", true);
            // 条件更新 graded=0→1：并发重判/交卷判题只会有一次落分、一次错题、一次事件
            int rows = answerMapper.update(null, new LambdaUpdateWrapper<ExamAnswer>()
                    .eq(ExamAnswer::getId, a.getId())
                    .eq(ExamAnswer::getGraded, 0)
                    .set(ExamAnswer::getGraded, 1)
                    .set(ExamAnswer::getGradeType, ExamAnswer.GRADE_JUDGE)
                    .set(ExamAnswer::getScore, scaled)
                    .set(ExamAnswer::getIsCorrect, correct ? 1 : 0)
                    .set(ExamAnswer::getGradeDetail, writeJsonSafe(detail))
                    .set(ExamAnswer::getUpdateTime, LocalDateTime.now()));
            if (rows == 0) {
                continue;
            }
            evidenceReceipts.captureRequired(userId, "EXAM_ANSWER", a.getId());
            if (!correct && JudgeStatus.isUserFailure(sub.getJudgeStatus())) {
                collectCodeMistake(pq, readSnapshot(pq.getSnapshot()), sub, userId);
            }
            recordAnswerEvent(pq, correct, scaled, userId);
        }

        // 汇总重算（与 AI 补评同一口径：只统计已评分题）
        List<ExamAnswer> all = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getAttemptId, attemptId));
        int remainJudge = (int) all.stream()
                .filter(a -> a.getGraded() != null && a.getGraded() == 0
                        && ExamAnswer.GRADE_JUDGE.equals(a.getGradeType())).count();
        int remainAi = (int) all.stream()
                .filter(a -> a.getGraded() != null && a.getGraded() == 0
                        && ExamAnswer.GRADE_PENDING.equals(a.getGradeType())).count();
        int totalScore = all.stream().filter(a -> a.getScore() != null).mapToInt(ExamAnswer::getScore).sum();
        int maxScore = all.stream().filter(a -> a.getGraded() != null && a.getGraded() == 1)
                .mapToInt(ExamAnswer::getMaxScore).sum();
        int graded = (int) all.stream().filter(a -> a.getGraded() != null && a.getGraded() == 1).count();
        int correct = (int) all.stream().filter(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1).count();

        // AI 评卷正在进行时不覆盖其认领态（题目分数已条件落库，待 AI 收尾统一汇总）
        if (!ExamAttempt.GRADING_AI_RUNNING.equals(attempt.getGradingStatus())) {
            String gradingStatus = remainAi > 0 ? ExamAttempt.GRADING_PENDING_AI
                    : remainJudge > 0 ? ExamAttempt.GRADING_PENDING_JUDGE
                    : ExamAttempt.GRADING_NONE;
            String status = remainAi + remainJudge > 0
                    ? ExamAttempt.STATUS_GRADING : ExamAttempt.STATUS_GRADED;
            attemptMapper.update(null, new LambdaUpdateWrapper<ExamAttempt>()
                    .eq(ExamAttempt::getId, attemptId)
                    .set(ExamAttempt::getTotalScore, totalScore)
                    .set(ExamAttempt::getMaxScore, maxScore)
                    .set(ExamAttempt::getGradedCount, graded)
                    .set(ExamAttempt::getCorrectCount, correct)
                    .set(ExamAttempt::getQuestionCount, all.size())
                    .set(ExamAttempt::getGradingStatus, gradingStatus)
                    .set(ExamAttempt::getStatus, status)
                    .set(ExamAttempt::getUpdateTime, LocalDateTime.now()));
        }
        return buildResultVO(attemptMapper.selectById(attemptId));
    }

    private List<ExamAnswer> listPendingAnswers(Long attemptId) {
        // Phase 3G：AI 只处理 PENDING 主观题；CODE 待判题为 gradeType=JUDGE，绝不能被 AI 补评
        return answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getAttemptId, attemptId)
                .eq(ExamAnswer::getGraded, 0)
                .eq(ExamAnswer::getGradeType, ExamAnswer.GRADE_PENDING));
    }

    private int paperDurationSeconds(Long paperId) {
        ExamPaper p = paperMapper.selectById(paperId);
        return p == null ? 0 : p.getDurationMinutes() * 60;
    }

    private List<ExamPaperQuestion> listPaperQuestions(Long paperId) {
        return paperQuestionMapper.selectList(new LambdaQueryWrapper<ExamPaperQuestion>()
                .eq(ExamPaperQuestion::getPaperId, paperId)
                .orderByAsc(ExamPaperQuestion::getOrdinal));
    }

    /** 草稿版本保存在原JSON内；历史无revision的草稿视为0，不新建考试状态表。 */
    private long draftRevision(String draft) {
        if (draft == null || draft.isBlank()) return 0;
        try {
            JsonNode node = objectMapper.readTree(draft);
            if (node == null || !node.isObject()) throw new BusinessException(409,"服务端草稿异常，请联系支持");
            JsonNode revision = node.get("revision");
            if (revision == null) return 0;
            if (!revision.isIntegralNumber() || !revision.canConvertToLong() || revision.asLong()<0) throw new BusinessException(409,"服务端草稿版本异常");
            return revision.asLong();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException(409,"服务端草稿无法读取"); }
    }

    private Map<Long, String> readDraftAnswers(String draft) {
        if (draft == null || draft.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(draft);
            Map<Long, String> map = new HashMap<>();
            JsonNode answers = node.path("answers");
            answers.fields().forEachRemaining(e -> {
                try {
                    map.put(Long.parseLong(e.getKey()), e.getValue().asText(""));
                } catch (NumberFormatException ignored) {
                    //
                }
            });
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private ExamPaper getAccessiblePaper(Long paperId, Long userId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null || paper.getStatus() != 1) {
            throw new BusinessException(404, "试卷不存在或已失效");
        }
        if (paper.getOwnerUserId() != null && !paper.getOwnerUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该试卷");
        }
        return paper;
    }

    private ExamAttempt getOwnedAttemptForUpdate(Long attemptId, Long userId) {
        ExamAttempt attempt = attemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>().eq(ExamAttempt::getId,attemptId).last("FOR UPDATE"));
        if (attempt == null) throw new BusinessException(404,"考试记录不存在");
        if (!Objects.equals(attempt.getUserId(),userId)) throw new BusinessException(403,"无权访问该考试记录");
        return attempt;
    }

    private ExamAttempt getOwnedAttempt(Long attemptId, Long userId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BusinessException(404, "考试记录不存在");
        }
        if (!attempt.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该考试记录");
        }
        return attempt;
    }

    private ExamVos.AttemptVO buildAttemptVO(ExamAttempt attempt, ExamPaper paper) {
        ExamVos.AttemptVO vo = new ExamVos.AttemptVO();
        vo.setAttemptId(attempt.getId());
        vo.setPaperId(paper.getId());
        vo.setPaperName(paper.getName());
        vo.setMode(attempt.getMode());
        vo.setStatus(attempt.getStatus());
        vo.setStartTime(attempt.getStartTime());
        vo.setServerDeadline(attempt.getServerDeadline());
        vo.setServerNow(LocalDateTime.now());
        vo.setDurationMinutes(paper.getDurationMinutes());
        vo.setTotalScore(paper.getTotalScore());
        vo.setPassScore(paper.getPassScore());
        vo.setQuestionCount(attempt.getQuestionCount());
        vo.setGradingStatus(attempt.getGradingStatus());
        vo.setDeadlineExpired(LocalDateTime.now().isAfter(attempt.getServerDeadline()));

        Map<Long, String> draftAnswers = readDraftAnswers(attempt.getDraft());
        vo.setDraft(draftAnswers);
        vo.setDraftRevision(draftRevision(attempt.getDraft()));
        List<Integer> marked = new ArrayList<>();
        if (attempt.getDraft() != null) {
            try {
                JsonNode node = objectMapper.readTree(attempt.getDraft());
                node.path("marked").forEach(n -> marked.add(n.asInt()));
            } catch (Exception ignored) {
                //
            }
        }
        if (attempt.getMarked() != null) {
            try {
                marked.clear();
                objectMapper.readTree(attempt.getMarked()).forEach(n -> marked.add(n.asInt()));
            } catch (Exception ignored) {
                //
            }
        }
        vo.setMarked(marked);

        List<ExamVos.TakeQuestionVO> qs = new ArrayList<>();
        for (ExamPaperQuestion pq : listPaperQuestions(paper.getId())) {
            JsonNode snap = readSnapshot(pq.getSnapshot());
            ExamVos.TakeQuestionVO q = new ExamVos.TakeQuestionVO();
            q.setPaperQuestionId(pq.getId());
            q.setOrdinal(pq.getOrdinal());
            q.setTitle(snap.path("title").asText(""));
            q.setContent(snap.path("content").asText(""));
            q.setQuestionType(pq.getQuestionType());
            List<String> options = new ArrayList<>();
            snap.path("options").forEach(o -> options.add(o.asText()));
            q.setOptions(options);
            q.setScore(pq.getScore());
            q.setDifficulty(snap.path("difficulty").asInt(2));
            q.setTopic(pq.getTopic());
            JsonNode codeNode = snap.get("codeProblem");
            if (codeNode != null && codeNode.isObject()) {
                q.setCodeProblem(objectMapper.convertValue(codeNode,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        }));
            }
            qs.add(q);
        }
        vo.setQuestions(qs);
        return vo;
    }

    private ExamVos.ResultVO buildResultVO(ExamAttempt attempt) {
        ExamVos.ResultVO vo = new ExamVos.ResultVO();
        vo.setAttemptId(attempt.getId());
        vo.setPaperId(attempt.getPaperId());
        ExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        vo.setPaperName(paper == null ? "" : paper.getName());
        vo.setMode(attempt.getMode());
        vo.setStatus(attempt.getStatus());
        vo.setGradingStatus(attempt.getGradingStatus());
        vo.setTotalScore(attempt.getTotalScore());
        vo.setMaxScore(attempt.getMaxScore());
        vo.setPaperMaxScore(attempt.getPaperMaxScore());
        vo.setCorrectCount(attempt.getCorrectCount());
        vo.setGradedCount(attempt.getGradedCount());
        vo.setQuestionCount(attempt.getQuestionCount());
        vo.setPassScore(attempt.getPaperMaxScore() == null ? 60 : paper.getPassScore());
        vo.setSubmitTime(attempt.getSubmitTime());
        vo.setDurationSeconds(attempt.getDurationSeconds());
        vo.setDeadlineExpired(attempt.getSubmitTime() != null && attempt.getServerDeadline() != null
                && attempt.getSubmitTime().isAfter(attempt.getServerDeadline()));
        vo.setAiAvailable(examAi.isConfigured());
        if (ExamAttempt.GRADING_PENDING_AI.equals(attempt.getGradingStatus())) {
            vo.setAiMessage("存在待 AI 评分的主观题，当前为暂定成绩（未评分题不计 0 分），可发起 AI 辅助评分。");
        } else if (ExamAttempt.GRADING_AI_FAILED.equals(attempt.getGradingStatus())) {
            vo.setAiMessage("部分主观题 AI 评分失败，保持待评状态，可重试；分数不按 0 计。");
        } else if (ExamAttempt.GRADING_AI_RUNNING.equals(attempt.getGradingStatus())) {
            vo.setAiMessage("AI 辅助评分进行中，请稍后刷新查看结果，勿重复发起。");
        } else if (ExamAttempt.GRADING_PENDING_JUDGE.equals(attempt.getGradingStatus())) {
            vo.setAiMessage("代码题正在等待判题服务出分（判题服务未就绪时保持待判，不计 0 分），可稍后重试判题。");
        }

        List<ExamPaperQuestion> pqs = listPaperQuestions(attempt.getPaperId());
        Map<Long, ExamPaperQuestion> pqMap = pqs.stream()
                .collect(Collectors.toMap(ExamPaperQuestion::getId, pq -> pq));
        List<ExamAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getAttemptId, attempt.getId())
                .orderByAsc(ExamAnswer::getOrdinal));
        List<ExamVos.ResultItemVO> items = new ArrayList<>();
        for (ExamAnswer a : answers) {
            ExamPaperQuestion pq = pqMap.get(a.getPaperQuestionId());
            if (pq == null) {
                continue;
            }
            JsonNode snap = readSnapshot(pq.getSnapshot());
            ExamVos.ResultItemVO item = new ExamVos.ResultItemVO();
            item.setPaperQuestionId(pq.getId());
            item.setOrdinal(pq.getOrdinal());
            item.setTopic(pq.getTopic());
            item.setTitle(snap.path("title").asText(""));
            item.setContent(snap.path("content").asText(""));
            item.setQuestionType(pq.getQuestionType());
            List<String> options = new ArrayList<>();
            snap.path("options").forEach(o -> options.add(o.asText()));
            item.setOptions(options);
            item.setScore(a.getScore());
            item.setMaxScore(a.getMaxScore());
            item.setDifficulty(snap.path("difficulty").asInt(2));
            item.setUserAnswer(a.getUserAnswer());
            String standardRaw = snap.path("answer").asText("");
            item.setCorrectAnswer(standardRaw);
            item.setCorrectAnswerText(displayAnswer(pq.getQuestionType(), standardRaw, snap));
            item.setExplanation(snap.path("explanation").asText(""));
            item.setCorrect(a.getIsCorrect() == null ? null : a.getIsCorrect() == 1);
            item.setGradeType(a.getGradeType());
            item.setGraded(a.getGraded() != null && a.getGraded() == 1);

            if (ExamAnswer.GRADE_AI.equals(a.getGradeType()) && a.getGradeDetail() != null) {
                try {
                    JsonNode detail = objectMapper.readTree(a.getGradeDetail());
                    List<ExamVos.RubricDimension> rubric = new ArrayList<>();
                    JsonNode dims = detail.path("dimensions");
                    dims.fields().forEachRemaining(e -> {
                        ExamVos.RubricDimension d = new ExamVos.RubricDimension();
                        d.setKey(e.getKey());
                        d.setName(dimName(e.getKey()));
                        d.setWeight(ExamAiService.RUBRIC_WEIGHTS.getOrDefault(e.getKey(), 0));
                        d.setScore(e.getValue().asInt(0));
                        d.setMaxScore(100);
                        rubric.add(d);
                    });
                    item.setRubric(rubric);
                    item.setHitPoints(textList(detail.path("hitPoints")));
                    item.setMissingPoints(textList(detail.path("missingPoints")));
                    item.setErrors(textList(detail.path("errors")));
                    item.setSuggestions(textList(detail.path("suggestions")));
                    item.setConfidence(detail.path("confidence").asInt(0));
                    item.setAiGradeNote(detail.path("note").asText("AI 辅助评分，非人工审核成绩"));
                } catch (Exception e) {
                    item.setAiGradeNote("AI 评分证据解析失败");
                }
            }
            items.add(item);
        }
        vo.setItems(items);
        vo.setTypeAnalysis(buildGroupAnalysis(answers, pqMap, true));
        vo.setTopicAnalysis(buildGroupAnalysis(answers, pqMap, false));

        boolean complete = ExamAttempt.STATUS_GRADED.equals(attempt.getStatus());
        vo.setPassed(complete && paper != null && attempt.getTotalScore() != null
                && attempt.getTotalScore() >= paper.getPassScore());
        return vo;
    }

    private List<ExamVos.GroupAnalysis> buildGroupAnalysis(List<ExamAnswer> answers,
                                                           Map<Long, ExamPaperQuestion> pqMap, boolean byType) {
        Map<String, List<ExamAnswer>> groups = new LinkedHashMap<>();
        for (ExamAnswer a : answers) {
            ExamPaperQuestion pq = pqMap.get(a.getPaperQuestionId());
            if (pq == null) {
                continue;
            }
            String key = byType ? pq.getQuestionType() : (pq.getTopic() == null || pq.getTopic().isBlank() ? "未分类" : pq.getTopic());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        List<ExamVos.GroupAnalysis> out = new ArrayList<>();
        groups.forEach((key, list) -> {
            ExamVos.GroupAnalysis g = new ExamVos.GroupAnalysis();
            g.setKey(key);
            g.setName(byType ? typeName(key) : key);
            g.setCount(list.size());
            int graded = (int) list.stream().filter(a -> a.getGraded() != null && a.getGraded() == 1).count();
            g.setGradedCount(graded);
            int score = list.stream().filter(a -> a.getScore() != null).mapToInt(ExamAnswer::getScore).sum();
            int max = list.stream().filter(a -> a.getGraded() != null && a.getGraded() == 1)
                    .mapToInt(ExamAnswer::getMaxScore).sum();
            int correct = (int) list.stream().filter(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1).count();
            g.setScore(score);
            g.setMaxScore(max);
            g.setCorrectCount(correct);
            g.setRate(graded == 0 || max == 0 ? null : (int) Math.round(score * 100.0 / max));
            out.add(g);
        });
        return out;
    }

    private Set<String> loadBankFingerprints() {
        Set<String> fps = new HashSet<>();
        for (QuizQuestion q : composer.loadAccessibleBank()) {
            fps.add(SemanticIdentity.fingerprint(q.getQuestionType(), q.getTitle(), q.getContent()));
        }
        return fps;
    }

    private Map<String, Integer> loadTopicCounts(Long userId, List<String> topics) {
        return composer.topicCounts(userId, topics);
    }

    private JsonNode readSnapshot(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> textList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> {
                String s = n.asText("");
                if (!s.isBlank()) {
                    out.add(s);
                }
            });
        }
        return out;
    }

    private String displayAnswer(String type, String answer, JsonNode snap) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String a = answer.trim();
        return switch (type == null ? "" : type) {
            case QuizQuestion.TYPE_SINGLE_CHOICE -> optionText(snap, a);
            case QuizQuestion.TYPE_MULTIPLE_CHOICE -> ArraysSplit(a).stream()
                    .map(idx -> optionText(snap, idx)).collect(Collectors.joining("；"));
            case QuizQuestion.TYPE_TRUE_FALSE -> "true".equalsIgnoreCase(a) ? "正确" : "错误";
            default -> a;
        };
    }

    private List<String> ArraysSplit(String a) {
        List<String> out = new ArrayList<>();
        for (String s : a.split(",")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private String optionText(JsonNode snap, String idxStr) {
        try {
            int idx = Integer.parseInt(idxStr.trim());
            JsonNode options = snap.path("options");
            if (options.isArray() && idx >= 0 && idx < options.size()) {
                return (char) ('A' + idx) + ". " + options.get(idx).asText();
            }
        } catch (NumberFormatException ignored) {
            //
        }
        return idxStr;
    }

    private ExamVos.TemplateVO toTemplateVO(ExamTemplate t) {
        ExamVos.TemplateVO vo = new ExamVos.TemplateVO();
        vo.setId(t.getId());
        vo.setTemplateKey(t.getTemplateKey());
        vo.setName(t.getName());
        vo.setDescription(t.getDescription());
        vo.setDomain(t.getDomain());
        vo.setTopics(t.getTopics() == null ? List.of() : List.of(t.getTopics().split(",")));
        vo.setDurationMinutes(t.getDurationMinutes());
        vo.setTotalScore(t.getTotalScore());
        vo.setPassScore(t.getPassScore());
        vo.setMode(t.getMode());
        vo.setVersion(t.getVersion());
        try {
            JsonNode spec = objectMapper.readTree(t.getSpec());
            vo.setTypeDistribution(readIntMap(spec.get("typeDistribution")));
            vo.setMultiPolicy(spec.path("multiPolicy").asText(ExamGrader.POLICY_PARTIAL_CREDIT));
            vo.setTopics(readStringList(spec.get("topics")));
        } catch (Exception e) {
            vo.setTypeDistribution(Map.of());
        }
        return vo;
    }

    private ExamVos.PaperVO toPaperVO(ExamPaper p, int questionCount) {
        ExamVos.PaperVO vo = new ExamVos.PaperVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setDomain(p.getDomain());
        vo.setTopics(p.getTopics() == null ? List.of()
                : List.of(p.getTopics().split(",")).stream().filter(s -> !s.isEmpty()).toList());
        vo.setSource(p.getSource());
        vo.setTemplateKey(p.getTemplateKey());
        vo.setStrategy(p.getStrategy());
        vo.setSeedValue(p.getSeedValue());
        vo.setDurationMinutes(p.getDurationMinutes());
        vo.setTotalScore(p.getTotalScore());
        vo.setPassScore(p.getPassScore());
        vo.setMode(p.getMode());
        vo.setShuffleQuestion(p.getShuffleQuestion());
        vo.setShuffleOption(p.getShuffleOption());
        vo.setAllowPause(p.getAllowPause());
        vo.setAllowChange(p.getAllowChange());
        vo.setReviewStatus(p.getReviewStatus());
        vo.setOwnerUserId(p.getOwnerUserId());
        vo.setQuestionCount(questionCount);
        vo.setCreatedAt(p.getCreateTime() == null ? null
                : p.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        if (p.getAiMeta() != null) {
            try {
                JsonNode meta = objectMapper.readTree(p.getAiMeta());
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("model", meta.path("model").asText(""));
                info.put("generatedAt", meta.path("generatedAt").asText(""));
                info.put("reviewStatus", meta.path("reviewStatus").asText("PENDING_REVIEW"));
                vo.setAiInfo(info);
            } catch (Exception ignored) {
                //
            }
        }
        return vo;
    }

    private ExamVos.AttemptListItemVO toListItem(ExamAttempt a) {
        ExamVos.AttemptListItemVO vo = new ExamVos.AttemptListItemVO();
        vo.setAttemptId(a.getId());
        vo.setPaperId(a.getPaperId());
        ExamPaper paper = paperMapper.selectById(a.getPaperId());
        vo.setPaperName(paper == null ? "未知试卷" : paper.getName());
        vo.setMode(a.getMode());
        vo.setStatus(a.getStatus());
        vo.setGradingStatus(a.getGradingStatus());
        vo.setTotalScore(a.getTotalScore());
        vo.setMaxScore(a.getMaxScore());
        vo.setPaperMaxScore(a.getPaperMaxScore());
        vo.setCorrectCount(a.getCorrectCount());
        vo.setGradedCount(a.getGradedCount());
        vo.setQuestionCount(a.getQuestionCount());
        vo.setStartTime(a.getStartTime());
        vo.setSubmitTime(a.getSubmitTime());
        vo.setDurationSeconds(a.getDurationSeconds());
        vo.setPassScore(paper == null ? 60 : paper.getPassScore());
        vo.setPassed(ExamAttempt.STATUS_GRADED.equals(a.getStatus()) && a.getTotalScore() != null
                && paper != null && a.getTotalScore() >= paper.getPassScore());
        return vo;
    }

    private ExamVos.ExamMistakeVO toExamMistakeVO(LearningMistake m) {
        ExamVos.ExamMistakeVO vo = new ExamVos.ExamMistakeVO();
        vo.setId(m.getId());
        vo.setQuestion(m.getQuestion());
        vo.setWrongAnswer(m.getWrongAnswer());
        vo.setCorrectAnswer(m.getCorrectAnswer());
        vo.setTopic(m.getCategory());
        vo.setDifficulty(m.getDifficulty());
        vo.setReviewCount(m.getReviewCount());
        vo.setMastered(m.getMastered() != null && m.getMastered() == 1);
        vo.setLastReviewTime(m.getLastReviewTime());
        vo.setCreateTime(m.getCreateTime());
        vo.setQuestionId(m.getQuestionId());
        return vo;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                String s = n.asText("");
                if (!s.isBlank()) {
                    out.add(s);
                }
            });
        }
        return out;
    }

    private Map<String, Integer> readIntMap(JsonNode node) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asInt(0)));
        }
        return out;
    }

    private String dimName(String key) {
        return switch (key) {
            case "technicalAccuracy" -> "技术准确性";
            case "principle" -> "原理理解";
            case "completeness" -> "完整性";
            case "engineering" -> "工程实践";
            case "expression" -> "表达结构";
            default -> key;
        };
    }

    private String typeName(String key) {
        return switch (key) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "主观题";
            default -> key;
        };
    }
}
