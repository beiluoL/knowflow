package com.knowflow.exam.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.entity.CodeTestCase;
import com.knowflow.coding.mapper.CodeProblemMapper;
import com.knowflow.coding.mapper.CodeTestCaseMapper;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 确定性规则组卷引擎：不调用 LLM。
 *
 * <p>支持策略：RANDOM / GRADIENT / BALANCED / FREQUENCY / MISTAKE / WEAK / SCENARIO / REVIEW。
 * 同一 seed + 同一题库版本 + 同一组卷输入 = 同一份题目序列（可复现）。
 * 题目按稳定语义身份去重；题库不足时输出 shortage，由上层返回 409 而不是静默重复。
 *
 * <p>Phase 3H：REVIEW 智能练习策略按可解释优先级融合「到期复习 → 高频错题 → 薄弱知识点
 * → 目标知识点 → 未做新题」，每道题带 {@link SelectionReason}；结果带规则版本
 * {@link #RULE_ENGINE_VERSION} 与选题原因统计。
 */
@Component
@RequiredArgsConstructor
public class PaperComposer {

    public static final String STRATEGY_RANDOM = "RANDOM";
    public static final String STRATEGY_GRADIENT = "GRADIENT";
    public static final String STRATEGY_BALANCED = "BALANCED";
    public static final String STRATEGY_FREQUENCY = "FREQUENCY";
    public static final String STRATEGY_MISTAKE = "MISTAKE";
    public static final String STRATEGY_WEAK = "WEAK";
    public static final String STRATEGY_SCENARIO = "SCENARIO";
    /** Phase 3H：智能练习融合策略（可解释优先级 + 配置化权重）。 */
    public static final String STRATEGY_REVIEW = "REVIEW";

    /** 规则引擎版本：选择口径/优先级发生变化时必须升版，便于审计与前端展示。 */
    public static final String RULE_ENGINE_VERSION = "rule-engine-v2";

    /** WEAK 策略要求每个知识点至少有这么多真实作答样本才作数，避免伪造薄弱点。 */
    private static final int WEAK_MIN_SAMPLES = 3;

    /** REVIEW 策略中“薄弱知识点”桶的错误率门槛；样本数门槛同为 {@link #WEAK_MIN_SAMPLES}。 */
    private static final double WEAK_RATE_THRESHOLD = 0.4;

    private final QuizQuestionMapper quizQuestionMapper;
    private final LearningMistakeMapper mistakeMapper;
    private final QuizAnswerRecordMapper answerRecordMapper;
    private final DocumentAccessPolicy documentAccess;
    private final DocCategoryMapper categoryMapper;
    private final DocDocumentMapper docMapper;
    private final CodeProblemMapper codeProblemMapper;
    private final CodeTestCaseMapper codeTestCaseMapper;
    private final ObjectMapper objectMapper;
    private final SmartPracticeProperties smartPracticeProperties;
    private final QuestionHistoryLookup historyLookup;

    /**
     * 题目显式知识点归属（Phase 3G）：优先 quiz_question.topic_code，
     * 历史题回退 tags 首段推导。任何新逻辑不得只依赖 tags。
     */
    public static String topicOfQuestion(QuizQuestion q) {
        if (q == null) {
            return "";
        }
        if (q.getTopicCode() != null && !q.getTopicCode().isBlank()) {
            return q.getTopicCode().trim().toUpperCase(java.util.Locale.ROOT);
        }
        return SemanticIdentity.topicOf(q.getTags());
    }

    /** 被选中的题目及其在试卷中的编排信息。 */
    public static class SelectedQuestion {
        public QuizQuestion question;
        public String topic;
        public int ordinal;
        public int score;
        /** Phase 3H：选题原因（{@link SelectionReason}），可解释、可展示。 */
        public String reason;

        public SelectedQuestion(QuizQuestion question, String topic, int ordinal, int score) {
            this(question, topic, ordinal, score, SelectionReason.GENERAL_POOL);
        }

        public SelectedQuestion(QuizQuestion question, String topic, int ordinal, int score, String reason) {
            this.question = question;
            this.topic = topic;
            this.ordinal = ordinal;
            this.score = score;
            this.reason = reason;
        }
    }

    /** 组卷结果：不足时 sufficient=false 并带缺口统计。 */
    public static class SelectionResult {
        public boolean sufficient;
        public String message;
        public List<SelectedQuestion> selected = new ArrayList<>();
        public Map<String, Integer> requested = new LinkedHashMap<>();
        public Map<String, Integer> available = new LinkedHashMap<>();
        public Set<String> topicsUsed = new HashSet<>();
        public String bankVersion;
        /** Phase 3H：规则引擎版本与选题原因统计。 */
        public String ruleVersion = RULE_ENGINE_VERSION;
        public Map<String, Integer> reasonCounts = new LinkedHashMap<>();
    }

    /** 题库可用性统计（供组卷页实时预览）。 */
    public SelectionResult preview(Long userId, List<String> topics, Map<String, Integer> requestedRaw,
                                   List<Integer> difficulties, String strategy) {
        return preview(userId,topics,requestedRaw,difficulties,strategy,true);
    }
    public SelectionResult preview(Long userId,List<String> topics,Map<String,Integer> requestedRaw,List<Integer> difficulties,String strategy,Boolean includeAnswered) {
        List<QuizQuestion> bank = loadBank(userId);
        List<QuizQuestion> filtered = filterByTopics(bank, topics);
        if (difficulties != null && !difficulties.isEmpty()) {
            Set<Integer> ds = new HashSet<>(difficulties);
            filtered = filtered.stream().filter(q -> ds.contains(q.getDifficulty())).toList();
        }
        if(Boolean.FALSE.equals(includeAnswered)) filtered=unansweredPool(userId,filtered,bank);
        Map<String, Integer> requested = normalizeRequested(requestedRaw, filtered);
        Map<String, Integer> available = new LinkedHashMap<>();
        for (String type : List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK","SHORT_ANSWER","CODE")) {
            long c = filtered.stream().filter(q -> type.equals(q.getQuestionType())).map(PaperComposer::questionIdentity).distinct().count();
            available.put(type, (int) c);
        }
        SelectionResult r = new SelectionResult();
        r.requested = requested;
        r.available = available;
        r.bankVersion = bankFingerprint(bank);
        boolean ok = true;
        List<String> gaps = new ArrayList<>();
        for (Map.Entry<String, Integer> e : requested.entrySet()) {
            int av = available.getOrDefault(e.getKey(), 0);
            if (av < e.getValue()) {
                ok = false;
                gaps.add(e.getKey() + " 需要 " + e.getValue() + " 题，题库仅有 " + av + " 题");
            }
        }
        long unique=filtered.stream().map(PaperComposer::questionIdentity).distinct().count();
        if(requested.values().stream().mapToInt(Integer::intValue).sum()>unique) {ok=false;gaps.add("按题目身份去重后总共仅有 "+unique+" 题");}
        r.sufficient = ok;
        r.message = ok ? null : "题库题量不足：" + String.join("；", gaps) + "。请调整题型、数量、知识点或难度。";
        return r;
    }

    /**
     * MISTAKE 策略专用：按用户未掌握错题在可访问题库中的实际题型构成生成题型分布，
     * 避免用通用默认分布（含主观题）去要求错题池里根本不存在的题型。
     * 题型顺序与默认分布一致；无未掌握错题或均不可访问时返回空 Map。
     */
    public Map<String, Integer> mistakeTypeDistribution(Long userId, List<String> topics) {
        Set<Long> mistakeIds = loadMistakeQuestionIds(userId);
        if (mistakeIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<String> normalizedTopics = topics == null ? List.of()
                : topics.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<QuizQuestion> pool = filterByTopics(loadBank(userId), normalizedTopics);
        Map<String, Long> raw = pool.stream()
                .filter(q -> mistakeIds.contains(q.getId()))
                .collect(Collectors.groupingBy(QuizQuestion::getQuestionType, LinkedHashMap::new, Collectors.counting()));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String t : List.of(QuizQuestion.TYPE_SINGLE_CHOICE, QuizQuestion.TYPE_MULTIPLE_CHOICE,
                QuizQuestion.TYPE_TRUE_FALSE, QuizQuestion.TYPE_FILL_BLANK, QuizQuestion.TYPE_SHORT_ANSWER,
                QuizQuestion.TYPE_CODE)) {
            Long c = raw.get(t);
            if (c != null && c > 0) {
                out.put(t, c.intValue());
            }
        }
        // 兜底：题库若新增其他题型，也保留计数，不静默丢题
        raw.forEach((t, c) -> out.putIfAbsent(t, c.intValue()));
        return out;
    }

    /** 执行组卷。 */
    public SelectionResult compose(Long userId, ExamRequests.ComposeRequest req) {
        List<QuizQuestion> bank = loadBank(userId);
        List<String> topics = req.getTopics() == null ? List.of()
                : req.getTopics().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<QuizQuestion> pool = filterByTopics(bank, topics);
        if (req.getDifficulties() != null && !req.getDifficulties().isEmpty()) {
            Set<Integer> ds = new HashSet<>(req.getDifficulties());
            pool = pool.stream().filter(q -> ds.contains(q.getDifficulty())).toList();
        }

        if(Boolean.FALSE.equals(req.getIncludeAnswered())) pool=unansweredPool(userId,pool,bank);
        Map<String, Integer> requested = normalizeRequested(req.getTypeDistribution(), pool);
        int totalWanted = requested.values().stream().mapToInt(Integer::intValue).sum();
        long seed = req.getSeed() == null ? System.nanoTime() : req.getSeed();
        Random random = new Random(seed);
        String strategy = req.getStrategy() == null ? STRATEGY_RANDOM : req.getStrategy();

        // 策略相关的预计算
        Map<Long, Integer> frequency = loadFrequency();
        boolean mistakeStrategy = STRATEGY_MISTAKE.equals(strategy);
        boolean reviewStrategy = STRATEGY_REVIEW.equals(strategy);
        Map<Long, LearningMistake> activeMistakes = (mistakeStrategy || reviewStrategy)
                ? loadActiveMistakes(userId) : Map.of();
        Set<Long> mistakeQuestionIds = mistakeStrategy ? activeMistakes.keySet() : Set.of();
        Set<Long> dueMistakeIds = reviewStrategy ? dueMistakeIds(activeMistakes) : Set.of();
        Map<String, Double> weakTopicRates = (STRATEGY_WEAK.equals(strategy) || reviewStrategy)
                ? loadTopicErrorRates(userId) : Map.of();
        Set<Long> answeredIds = reviewStrategy
                ? loadAnsweredQuestionIds(userId) : Set.of();
        Set<String> targetTopics = topics.stream()
                .map(t -> t.toUpperCase(java.util.Locale.ROOT)).collect(Collectors.toSet());

        if (STRATEGY_MISTAKE.equals(strategy) && mistakeQuestionIds.isEmpty()) {
            SelectionResult r = new SelectionResult();
            r.sufficient = false;
            r.bankVersion = bankFingerprint(bank);
            r.message = "错题强化需要你有未掌握的错题记录（且错题关联题库题目）。当前没有可用错题，证据不足，无法组卷。";
            r.requested = requested;
            r.available = Collections.emptyMap();
            return r;
        }
        if (STRATEGY_WEAK.equals(strategy) && weakTopicRates.isEmpty()) {
            SelectionResult r = new SelectionResult();
            r.sufficient = false;
            r.bankVersion = bankFingerprint(bank);
            r.message = "薄弱点强化需要每个知识点至少 " + WEAK_MIN_SAMPLES + " 次真实作答样本。当前样本不足，无法判定薄弱点，已为你保留其他组卷策略。";
            r.requested = requested;
            r.available = Collections.emptyMap();
            return r;
        }

        SelectionResult result = new SelectionResult();
        result.requested = requested;
        result.available = new LinkedHashMap<>();
        Set<String> usedFingerprints = new HashSet<>();
        Set<Long> usedIds = new HashSet<>();
        List<SelectedQuestion> picked = new ArrayList<>();
        boolean sufficient = true;
        List<String> gaps = new ArrayList<>();

        for (Map.Entry<String, Integer> e : requested.entrySet()) {
            String type = e.getKey();
            int want = e.getValue();
            List<QuizQuestion> candidates = pool.stream()
                    .filter(q -> type.equals(q.getQuestionType()))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (STRATEGY_MISTAKE.equals(strategy)) {
                candidates = candidates.stream()
                        .filter(q -> mistakeQuestionIds.contains(q.getId()))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            if (STRATEGY_WEAK.equals(strategy)) {
                // 优先抽错误率最高的知识点下的题
                candidates.sort(Comparator.comparingDouble(
                        (QuizQuestion q) -> weakTopicRates.getOrDefault(topicOfQuestion(q), 0.0))
                        .reversed());
            } else if (STRATEGY_FREQUENCY.equals(strategy)) {
                candidates.sort(Comparator.comparingInt(
                        (QuizQuestion q) -> frequency.getOrDefault(q.getId(), 0)).reversed());
                // 同频内用 seed 打散，保证可复现
                Collections.shuffle(candidates, random);
                candidates.sort(Comparator.comparingInt(
                        (QuizQuestion q) -> frequency.getOrDefault(q.getId(), 0)).reversed());
            } else if (STRATEGY_SCENARIO.equals(strategy)) {
                candidates.sort(Comparator.comparingInt(QuizQuestion::getDifficulty).reversed());
                List<QuizQuestion> shuffled = new ArrayList<>(candidates);
                Collections.shuffle(shuffled, random);
                shuffled.sort(Comparator.comparingInt(QuizQuestion::getDifficulty).reversed());
                candidates = shuffled;
            } else if (STRATEGY_GRADIENT.equals(strategy)) {
                Collections.shuffle(candidates, random);
                // 难度由易到难
                candidates.sort(Comparator.comparingInt(q -> q.getDifficulty() == null ? 2 : q.getDifficulty()));
            } else if (STRATEGY_BALANCED.equals(strategy)) {
                candidates = balancedByTopic(candidates, random);
            } else if (reviewStrategy) {
                // 桶内 seeded 打散后按优先级桶稳定排序（List.sort 稳定，桶内保持随机序）
                Collections.shuffle(candidates, random);
                candidates.sort(Comparator.comparingInt(q -> reviewBucket(q, dueMistakeIds, activeMistakes,
                        weakTopicRates, targetTopics, answeredIds)));
            } else {
                Collections.shuffle(candidates, random);
            }

            int got;
            if (reviewStrategy) {
                got = pickReview(candidates, want, usedIds, usedFingerprints, picked, random,
                        dueMistakeIds, activeMistakes, weakTopicRates, targetTopics, answeredIds);
            } else {
                got = 0;
                for (QuizQuestion q : candidates) {
                    if (got >= want) {
                        break;
                    }
                    if (!tryReserve(q, usedIds, usedFingerprints)) {
                        continue;
                    }
                    usedIds.add(q.getId());
                    String reason = reasonFor(q, strategy, dueMistakeIds, activeMistakes,
                            weakTopicRates, answeredIds);
                    picked.add(new SelectedQuestion(q, topicOfQuestion(q), 0, 0, reason));
                    got++;
                }
            }
            result.available.put(type, candidates.size());
            if (got < want) {
                sufficient = false;
                gaps.add(type + " 需要 " + want + " 题，去重后仅可抽取 " + got + " 题");
            }
        }

        // 题目乱序（受 seed 控制）
        if (req.getShuffleQuestion() != null && req.getShuffleQuestion() == 1) {
            Collections.shuffle(picked, new Random(seed ^ 0x9E3779B97F4A7C15L));
        }

        // 分值分配：尽量等分，余数分给前若干题
        int totalScore = req.getTotalScore() == null || req.getTotalScore() <= 0 ? 100 : req.getTotalScore();
        int n = picked.size();
        int base = n == 0 ? 0 : totalScore / n;
        int remainder = n == 0 ? 0 : totalScore - base * n;
        for (int i = 0; i < n; i++) {
            SelectedQuestion sq = picked.get(i);
            sq.ordinal = i + 1;
            sq.score = base + (i < remainder ? 1 : 0);
            result.topicsUsed.add(sq.topic);
        }

        result.selected = picked;
        result.sufficient = sufficient && (totalWanted == n);
        result.bankVersion = bankFingerprint(bank);
        Map<String, Integer> reasonCounts = new LinkedHashMap<>();
        for (SelectedQuestion sq : picked) {
            reasonCounts.merge(sq.reason, 1, Integer::sum);
        }
        result.reasonCounts = reasonCounts;
        if (!result.sufficient) {
            result.message = "题库题量不足：" + String.join("；", gaps) + "。请调整题型、数量、知识点或难度，系统不会重复题目凑数。";
        }
        return result;
    }

    // ==================== Phase 3H：REVIEW 融合策略 ====================

    /** 桶序号 → 选题原因；顺序即优先级。GENERAL_POOL（5）仅作补位，不参与权重配额。 */
    private static final String[] BUCKET_REASONS = {
            SelectionReason.DUE_REVIEW,
            SelectionReason.HIGH_FREQ_MISTAKE,
            SelectionReason.WEAK_TOPIC,
            SelectionReason.TARGET_TOPIC,
            SelectionReason.NEW_UNANSWERED,
            SelectionReason.GENERAL_POOL
    };

    /** 计算单题在 REVIEW 策略中落入的优先级桶（0-5）。 */
    private int reviewBucket(QuizQuestion q, Set<Long> dueIds, Map<Long, LearningMistake> activeMistakes,
                             Map<String, Double> weakRates, Set<String> targetTopics, Set<Long> answeredIds) {
        Long id = q.getId();
        if (dueIds.contains(id)) {
            return 0;
        }
        if (activeMistakes.containsKey(id)) {
            return 1;
        }
        String topic = topicOfQuestion(q);
        if (weakRates.getOrDefault(topic, 0.0) >= WEAK_RATE_THRESHOLD) {
            return 2;
        }
        if (!targetTopics.isEmpty() && !topic.isEmpty() && targetTopics.contains(topic)) {
            return 3;
        }
        if (!answeredIds.contains(id)) {
            return 4;
        }
        return 5;
    }

    /** 非 REVIEW 策略的选题原因；未做过的题标 NEW_UNANSWERED（但绝不解释成“不会”）。 */
    private String reasonFor(QuizQuestion q, String strategy, Set<Long> dueIds,
                             Map<Long, LearningMistake> activeMistakes,
                             Map<String, Double> weakRates, Set<Long> answeredIds) {
        Long id = q.getId();
        if (STRATEGY_MISTAKE.equals(strategy)) {
            return dueIds.contains(id) ? SelectionReason.DUE_REVIEW : SelectionReason.HIGH_FREQ_MISTAKE;
        }
        if (STRATEGY_WEAK.equals(strategy)) {
            return SelectionReason.WEAK_TOPIC;
        }
        if (STRATEGY_FREQUENCY.equals(strategy)) {
            return SelectionReason.FREQUENCY;
        }
        return answeredIds.contains(id) ? SelectionReason.GENERAL_POOL : SelectionReason.NEW_UNANSWERED;
    }

    /** Explicit source identity wins. Titles are only a compatibility fallback for legacy rows without a key. */
    private static String questionIdentity(QuizQuestion q) {
        if(q.getCanonicalKey()!=null&&!q.getCanonicalKey().isBlank()) return "canonical:"+q.getCanonicalKey().trim();
        return "legacy:"+SemanticIdentity.fingerprint(q.getQuestionType(),q.getTitle(),q.getContent());
    }

    /** 去重占用判定：题目 ID 与语义指纹均未使用时才可入卷。 */
    private boolean tryReserve(QuizQuestion q, Set<Long> usedIds, Set<String> usedFingerprints) {
        if (usedIds.contains(q.getId())) {
            return false;
        }
        String fp = questionIdentity(q);
        return usedFingerprints.add(fp);
    }

    /**
     * REVIEW 策略选题：先按配置权重为 5 个优先级桶分配配额并依次选取，
     * 高优先级桶题量不足时，第二轮忽略配额按优先级顺序补位（GENERAL_POOL 兜底），
     * 保证总数尽量满足且优先级可解释。整个过程受 seed 控制、可复现。
     */
    private int pickReview(List<QuizQuestion> orderedByBucket, int want, Set<Long> usedIds,
                           Set<String> usedFingerprints, List<SelectedQuestion> picked, Random random,
                           Set<Long> dueIds, Map<Long, LearningMistake> activeMistakes,
                           Map<String, Double> weakRates, Set<String> targetTopics, Set<Long> answeredIds) {
        int[] quota = allocateBucketQuota(want, !targetTopics.isEmpty());
        int[] taken = new int[6];
        List<QuizQuestion> deferred = new ArrayList<>();
        int got = 0;
        for (QuizQuestion q : orderedByBucket) {
            if (got >= want) {
                break;
            }
            int b = reviewBucket(q, dueIds, activeMistakes, weakRates, targetTopics, answeredIds);
            if (b < 5 && taken[b] < quota[b] && tryReserve(q, usedIds, usedFingerprints)) {
                usedIds.add(q.getId());
                picked.add(new SelectedQuestion(q, topicOfQuestion(q), 0, 0, BUCKET_REASONS[b]));
                taken[b]++;
                got++;
            } else {
                deferred.add(q);
            }
        }
        // 补位：高优先级桶不足时，按已排序顺序（仍优先更低证据桶、最后常规题池）补齐
        for (QuizQuestion q : deferred) {
            if (got >= want) {
                break;
            }
            if (!tryReserve(q, usedIds, usedFingerprints)) {
                continue;
            }
            int b = reviewBucket(q, dueIds, activeMistakes, weakRates, targetTopics, answeredIds);
            usedIds.add(q.getId());
            picked.add(new SelectedQuestion(q, topicOfQuestion(q), 0, 0, BUCKET_REASONS[b]));
            got++;
        }
        return got;
    }

    /**
     * 按配置权重把 want 分配到 5 个优先级桶（最大余数法）；无目标知识点时剔除 TARGET_TOPIC
     * 权重并归一化。返回长度 6 的数组，index 5（GENERAL_POOL）配额恒为 0（仅补位）。
     */
    private int[] allocateBucketQuota(int want, boolean hasTargetTopic) {
        SmartPracticeProperties.Weights w = smartPracticeProperties.getWeights();
        int[] rawWeights = {
                Math.max(0, w.getDueReview()),
                Math.max(0, w.getHighFreqMistake()),
                Math.max(0, w.getWeakTopic()),
                hasTargetTopic ? Math.max(0, w.getTargetTopic()) : 0,
                Math.max(0, w.getNewUnanswered())
        };
        int totalWeight = java.util.Arrays.stream(rawWeights).sum();
        int[] quota = new int[6];
        if (totalWeight <= 0) {
            // 配置异常时退化：全部给最高优先级桶，由补位机制兜底
            quota[0] = want;
            return quota;
        }
        double[] fractions = new double[5];
        int assigned = 0;
        for (int i = 0; i < 5; i++) {
            double exact = (double) want * rawWeights[i] / totalWeight;
            quota[i] = (int) Math.floor(exact);
            fractions[i] = exact - quota[i];
            assigned += quota[i];
        }
        int remaining = want - assigned;
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        order.sort((a, b) -> Double.compare(fractions[b], fractions[a]));
        // 每轮至少有一个正权重桶分到 1 题，故最多 remaining 轮收敛
        while (remaining > 0) {
            for (int bucket : order) {
                if (remaining == 0) {
                    break;
                }
                if (rawWeights[bucket] > 0) {
                    quota[bucket]++;
                    remaining--;
                }
            }
        }
        return quota;
    }

    /** 用户未掌握错题 questionId → 错题实体。 */
    private Map<Long, LearningMistake> loadActiveMistakes(Long userId) {
        return mistakeMapper.selectList(new LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, userId)
                        .eq(LearningMistake::getMastered, 0)
                        .isNotNull(LearningMistake::getQuestionId)).stream()
                .filter(m -> m.getQuestionId() != null)
                .collect(Collectors.toMap(LearningMistake::getQuestionId, m -> m, (a, b) -> a));
    }

    /** 到期复习子集：未掌握且（从未复习或上次复习早于到期窗口）。窗口默认 1 天，与错题 dueToday 口径一致。 */
    private Set<Long> dueMistakeIds(Map<Long, LearningMistake> activeMistakes) {
        int dueDays = Math.max(0, smartPracticeProperties.getReviewDueDays());
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusDays(dueDays);
        return activeMistakes.values().stream()
                .filter(m -> m.getLastReviewTime() == null || m.getLastReviewTime().isBefore(threshold))
                .map(LearningMistake::getQuestionId)
                .collect(Collectors.toSet());
    }

    /** 用户历史作答过的题目 ID（真实事件）；未出现即“未做”，不代表“不会”。 */
    private Set<Long> loadAnsweredQuestionIds(Long userId) {
        Set<Long> ids = new HashSet<>(historyLookup.answered(userId));
        ids.addAll(answerRecordMapper.selectList(new LambdaQueryWrapper<QuizAnswerRecord>()
                        .eq(QuizAnswerRecord::getUserId, userId)).stream()
                .map(QuizAnswerRecord::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return ids;
    }

    private List<QuizQuestion> unansweredPool(Long userId,List<QuizQuestion> pool,List<QuizQuestion> bank) {
        Set<Long> answered=loadAnsweredQuestionIds(userId);
        Set<String> identities=bank.stream().filter(q->answered.contains(q.getId())).map(PaperComposer::questionIdentity).collect(Collectors.toSet());
        identities.addAll(historyLookup.answeredCanonicalKeys(userId).stream().map(k->"canonical:"+k.trim()).toList());
        return pool.stream().filter(q->!answered.contains(q.getId())&&!identities.contains(questionIdentity(q))).toList();
    }

    /** 知识点均衡：按 topic 轮询取题，topic 内使用 seeded 随机。 */
    private List<QuizQuestion> balancedByTopic(List<QuizQuestion> candidates, Random random) {
        Map<String, List<QuizQuestion>> byTopic = new LinkedHashMap<>();
        for (QuizQuestion q : candidates) {
            byTopic.computeIfAbsent(topicOfQuestion(q), k -> new ArrayList<>()).add(q);
        }
        byTopic.values().forEach(l -> Collections.shuffle(l, random));
        List<QuizQuestion> out = new ArrayList<>();
        boolean added = true;
        while (added) {
            added = false;
            for (List<QuizQuestion> l : byTopic.values()) {
                if (!l.isEmpty()) {
                    out.add(l.remove(0));
                    added = true;
                }
            }
        }
        return out;
    }

    /** 组卷输入未显式指定题型分布时，从题库可用题型中按题量均分。 */
    private Map<String, Integer> normalizeRequested(Map<String, Integer> raw, List<QuizQuestion> pool) {
        Map<String, Integer> requested = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> {
                if (k != null && v != null && v > 0) {
                    requested.put(k, v);
                }
            });
        }
        return requested;
    }

    private List<QuizQuestion> filterByTopics(List<QuizQuestion> bank, List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return bank;
        }
        Set<String> upper = topics.stream().map(t -> t.trim().toUpperCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        return bank.stream()
                .filter(q -> {
                    String t = topicOfQuestion(q);
                    return !t.isEmpty() && upper.contains(t);
                })
                .toList();
    }

    /** 复用 QuizService 同一套访问范围：非管理员只能取可读分类/文档下的题与无归属题。 */
    public List<QuizQuestion> loadAccessibleBank() {
        return loadBank(documentAccess.actor());
    }

    /** 当前用户可见题库中各知识点（tags 首标签）的题目数量。 */
    public Map<String, Integer> topicCounts(Long userId, List<String> topics) {
        List<QuizQuestion> bank = filterByTopics(loadBank(userId), topics);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QuizQuestion q : bank) {
            String topic = topicOfQuestion(q);
            if (!topic.isEmpty()) {
                counts.merge(topic, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** 复用 QuizService 同一套访问范围：非管理员只能取可读分类/文档下的题与无归属题。 */
    private List<QuizQuestion> loadBank(Long actor) {
        LambdaQueryWrapper<QuizQuestion> qw = new LambdaQueryWrapper<>();
        qw.eq(QuizQuestion::getStatus, 1);
        // Phase 3G：只有审核通过（或历史无审核标记）的题才能入卷，待审核/驳回题不进入组卷池
        qw.and(w -> w.isNull(QuizQuestion::getReviewStatus)
                .or().eq(QuizQuestion::getReviewStatus, QuizQuestion.REVIEW_APPROVED));
        if (!documentAccess.admin(actor)) {
            List<Long> categoryIds = categoryMapper.selectList(new LambdaQueryWrapper<DocCategory>())
                    .stream().filter(c -> documentAccess.canReadCategory(c.getId(), actor))
                    .map(DocCategory::getId).toList();
            List<Long> docIds = docMapper.selectList(documentAccess.scope(new LambdaQueryWrapper<DocDocument>(), actor))
                    .stream().map(DocDocument::getId).toList();
            qw.and(w -> {
                w.isNull(QuizQuestion::getCategoryId);
                if (!categoryIds.isEmpty()) {
                    w.or().in(QuizQuestion::getCategoryId, categoryIds);
                }
            });
            // Both explicit associations must be readable; a null category cannot bypass a private document.
            qw.and(w -> {
                w.isNull(QuizQuestion::getDocId);
                if (!docIds.isEmpty()) w.or().in(QuizQuestion::getDocId, docIds);
            });
        }
        return quizQuestionMapper.selectList(qw);
    }

    /** 历史作答频次（真实数据），供高频优先策略。 */
    private Map<Long, Integer> loadFrequency() {
        List<QuizAnswerRecord> all = answerRecordMapper.selectList(null);
        Map<Long, Integer> freq = new HashMap<>();
        for (QuizAnswerRecord r : all) {
            if (r.getQuestionId() != null) {
                freq.merge(r.getQuestionId(), 1, Integer::sum);
            }
        }
        return freq;
    }

    private Set<Long> loadMistakeQuestionIds(Long userId) {
        return mistakeMapper.selectList(new LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, userId)
                        .eq(LearningMistake::getMastered, 0)
                        .isNotNull(LearningMistake::getQuestionId))
                .stream().map(LearningMistake::getQuestionId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 基于真实答题记录计算知识点错误率；样本不足的知识点不输出（不伪造薄弱点）。 */
    private Map<String, Double> loadTopicErrorRates(Long userId) {
        List<QuizQuestion> questions = quizQuestionMapper.selectList(null);
        Map<Long, String> qidTopic = new HashMap<>();
        for (QuizQuestion q : questions) {
            qidTopic.put(q.getId(), topicOfQuestion(q));
        }
        Map<String, int[]> agg = new HashMap<>(); // topic -> [wrong, total]
        for (QuizAnswerRecord r : answerRecordMapper.selectList(
                new LambdaQueryWrapper<QuizAnswerRecord>().eq(QuizAnswerRecord::getUserId, userId))) {
            String topic = qidTopic.get(r.getQuestionId());
            if (topic == null || topic.isEmpty()) {
                continue;
            }
            int[] arr = agg.computeIfAbsent(topic, k -> new int[2]);
            arr[1]++;
            if (r.getIsCorrect() != null && r.getIsCorrect() == 0) {
                arr[0]++;
            }
        }
        Map<String, Double> rates = new HashMap<>();
        agg.forEach((topic, arr) -> {
            if (arr[1] >= WEAK_MIN_SAMPLES) {
                rates.put(topic, arr[0] * 1.0 / arr[1]);
            }
        });
        return rates;
    }

    /** 题库指纹：id+更新时间 的哈希，记录本次组卷所基于的题库版本。 */
    public String bankFingerprint(List<QuizQuestion> bank) {
        TreeMap<Long, String> pairs = new TreeMap<>();
        for (QuizQuestion q : bank) {
            pairs.put(q.getId(),q.getUpdateTime()+"|"+q.getVersion()+"|"+questionIdentity(q)+"|"+q.getDifficulty()+"|"+topicOfQuestion(q)+"|"+q.getReviewStatus());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pairs.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("qb-");
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            sb.append("-").append(bank.size());
            return sb.toString();
        } catch (Exception e) {
            return "qb-unknown-" + bank.size();
        }
    }

    /** 生成题目快照 JSON（含答案，仅服务端保存）。CODE 题附加公开判题信息，绝不包含参考解/隐藏用例。 */
    public String buildSnapshot(QuizQuestion q) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", q.getId());
            snap.put("catalogId", (QuizQuestion.TYPE_CODE.equals(q.getQuestionType()) ? "CODE_PROBLEM:" : "QUIZ:") + q.getId());
            snap.put("canonicalKey", q.getCanonicalKey());
            snap.put("title", q.getTitle());
            snap.put("content", q.getContent());
            snap.put("questionType", q.getQuestionType());
            snap.put("options", parseOptions(q.getOptions()));
            snap.put("answer", q.getAnswer());
            snap.put("explanation", q.getExplanation());
            snap.put("difficulty", q.getDifficulty());
            snap.put("topicCode", q.getTopicCode());
            snap.put("version", q.getVersion());
            if (QuizQuestion.TYPE_CODE.equals(q.getQuestionType())) {
                attachCodeSnapshot(snap, q.getId());
            }
            return objectMapper.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("题目快照序列化失败 questionId=" + q.getId(), e);
        }
    }

    /** CODE 题快照：仅考生作答必需信息 + 公开用例；参考解答与隐藏用例禁止入快照。 */
    private void attachCodeSnapshot(Map<String, Object> snap, Long questionId) {
        CodeProblem p = codeProblemMapper.selectOne(new LambdaQueryWrapper<CodeProblem>()
                .eq(CodeProblem::getQuestionId, questionId).last("LIMIT 1"));
        if (p == null) {
            return;
        }
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("problemId", p.getId());
        code.put("supportedLanguages", p.getSupportedLanguages());
        code.put("functionSignature", p.getFunctionSignature());
        code.put("starterCode", p.getStarterCode());
        code.put("constraints", p.getConstraints());
        code.put("examples", p.getExamples());
        code.put("timeLimitMs", p.getTimeLimitMs());
        code.put("memoryLimitMb", p.getMemoryLimitMb());
        code.put("judgeMode", p.getJudgeMode());
        code.put("testCaseVersion", p.getTestCaseVersion());
        List<Map<String, Object>> publicCases = codeTestCaseMapper.selectList(new LambdaQueryWrapper<CodeTestCase>()
                        .eq(CodeTestCase::getProblemId, p.getId())
                        .eq(CodeTestCase::getVisibility, CodeTestCase.VIS_PUBLIC)
                        .orderByAsc(CodeTestCase::getOrderNo)).stream()
                .map(tc -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("orderNo", tc.getOrderNo());
                    c.put("input", tc.getInput());
                    c.put("expectedOutput", tc.getExpectedOutput());
                    return c;
                }).toList();
        code.put("publicCases", publicCases);
        snap.put("codeProblem", code);
        // 明确不写入：referenceSolution / solutionExplanation / HIDDEN 用例
    }

    /** 解析 quiz_question.options 的 JSON 数组，异常时返回空列表。 */
    public List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(optionsJson, List.class);
            List<String> out = new ArrayList<>();
            for (Object o : raw) {
                out.add(String.valueOf(o));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
